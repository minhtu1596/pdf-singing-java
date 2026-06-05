package com.example.signingservice.service;

import com.example.signingservice.dto.EmbedSignatureResponse;
import com.example.signingservice.dto.PreparePdfRequest;
import com.example.signingservice.dto.PreparePdfResponse;
import com.example.signingservice.dto.VerifySignatureResponse;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.util.Store;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfService {

    private static final int DEFAULT_SIGNATURE_PLACEHOLDER_SIZE = 65_536;

    private static final int TYPE_SIGNATURE_INVISIBLE = 0;
    private static final int TYPE_SIGNATURE_TEXT = 1;
    private static final int TYPE_SIGNATURE_IMAGE = 2;
    private static final int TYPE_SIGNATURE_IMAGE_TEXT = 3;

    private static final float DEFAULT_SIGNATURE_X = 36f;
    private static final float DEFAULT_SIGNATURE_Y = 36f;
    private static final float DEFAULT_SIGNATURE_WIDTH = 220f;
    private static final float DEFAULT_SIGNATURE_HEIGHT = 70f;

    private static final byte[] ARIAL_FONT_BYTES;

    static {
        try (InputStream is = PdfService.class.getResourceAsStream("/fonts/Arial.ttf")) {
            if (is == null) {
                throw new FileNotFoundException("Font file Arial.ttf not found in resources");
            }
            ARIAL_FONT_BYTES = is.readAllBytes();
        } catch (IOException e) {
            throw new ExceptionInInitializerError("Failed to initialize Arial font: " + e.getMessage());
        }
    }

    public PreparePdfResponse preparePdf(PreparePdfRequest request) throws Exception {

        byte[] pdfBytes = decodeBase64(request.getPdfBase64(), "pdfBase64");
        byte[] normalizedPdfBytes = normalizePdfForSigning(pdfBytes);
        String hashAlgorithm = resolveHashAlgorithm(request.getHashalg());

        try (
                PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(normalizedPdfBytes));
                SignatureOptions signatureOptions = new SignatureOptions();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        ) {
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName(nonBlank(request.getSignaturename(), "External Signing"));
            signature.setReason("Document signed");
            signature.setSignDate(java.util.GregorianCalendar.from(Instant.now().atZone(java.time.ZoneId.systemDefault())));

            signatureOptions.setPreferredSignatureSize(DEFAULT_SIGNATURE_PLACEHOLDER_SIZE);
            document.addSignature(signature, signatureOptions);

            if (isVisibleSignature(request.getTypesignature())) {
                applyVisibleSignatureAppearance(document, signature, request);
            }

            ExternalSigningSupport externalSigning =
                    document.saveIncrementalForExternalSigning(outputStream);

            byte[] contentToSign = externalSigning.getContent().readAllBytes();
            byte[] hash = MessageDigest.getInstance(hashAlgorithm).digest(contentToSign);

            // Keep the reserved /Contents gap open by writing zero bytes as placeholder.
            externalSigning.setSignature(new byte[DEFAULT_SIGNATURE_PLACEHOLDER_SIZE]);

            PreparePdfResponse response = new PreparePdfResponse();
            response.setPreparedPdfBase64(Base64.getEncoder().encodeToString(outputStream.toByteArray()));
            response.setHashBase64(Base64.getEncoder().encodeToString(hash));
            response.setHashAlgorithm(hashAlgorithm);
            return response;
        }
    }

    private byte[] normalizePdfForSigning(byte[] pdfBytes) {
        try (
                PDDocument source = Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes));
                ByteArrayOutputStream normalizedOutput = new ByteArrayOutputStream()
        ) {
            // A full rewrite breaks existing signatures. Keep already-signed PDFs as-is
            // so next signatures can be appended incrementally.
            if (!source.getSignatureDictionaries().isEmpty()) {
                return pdfBytes;
            }

            // Rewrite once to stabilize xref/stream layout before creating signature ByteRange.
            source.save(normalizedOutput);
            return normalizedOutput.toByteArray();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Input PDF could not be normalized for signing", ex);
        }
    }

    public EmbedSignatureResponse embedSignature(
            String preparedPdfBase64,
            String signatureBase64
    ) {

        byte[] preparedPdfBytes = decodeBase64(preparedPdfBase64, "preparedPdfBase64");
        byte[] signatureBytes = decodeBase64(signatureBase64, "signatureBase64");

        byte[] signedPdfBytes = injectSignature(preparedPdfBytes, signatureBytes);

        EmbedSignatureResponse response = new EmbedSignatureResponse();
        response.setSignedPdfBase64(Base64.getEncoder().encodeToString(signedPdfBytes));
        response.setSavedFilePath(null);
        return response;
    }

    public VerifySignatureResponse verifySignature(String signedPdfBase64) {
        VerifySignatureResponse response = new VerifySignatureResponse();

        try {
            byte[] signedPdfBytes = decodeBase64(signedPdfBase64, "signedPdfBase64");
            ByteRangeInfo byteRangeInfo = parseByteRangeInfo(signedPdfBytes);
            response.setByteRangeValid(true);

            byte[] signedContent = buildSignedContent(signedPdfBytes, byteRangeInfo);
            byte[] fallbackHash = MessageDigest.getInstance("SHA-256").digest(signedContent);
            response.setSignedContentHashBase64(Base64.getEncoder().encodeToString(fallbackHash));

            byte[] signatureBytes = extractSignatureBytes(signedPdfBytes, byteRangeInfo);
            CMSSignedData cmsSignedData = new CMSSignedData(new CMSProcessableByteArray(signedContent), signatureBytes);
            response.setCmsParsed(true);

            SignerInformationStore signerInfos = cmsSignedData.getSignerInfos();
            if (signerInfos.getSigners().isEmpty()) {
                throw new IllegalArgumentException("No signer info found in CMS signature");
            }

            SignerInformation signerInformation = signerInfos.getSigners().iterator().next();
            String digestAlgorithm = mapDigestAlgorithmFromOid(signerInformation.getDigestAlgOID());
            response.setDigestAlgorithm(digestAlgorithm);

            byte[] signedContentHash;
            if ("SHA-256".equals(digestAlgorithm)) {
                signedContentHash = fallbackHash;
            } else {
                signedContentHash = MessageDigest.getInstance(digestAlgorithm).digest(signedContent);
                response.setSignedContentHashBase64(Base64.getEncoder().encodeToString(signedContentHash));
            }

            byte[] cmsDigest = extractMessageDigestFromSignedAttributes(signerInformation);
            if (cmsDigest != null) {
                response.setCmsMessageDigestBase64(Base64.getEncoder().encodeToString(cmsDigest));
                response.setMessageDigestMatch(Arrays.equals(cmsDigest, signedContentHash));
            }

            X509CertificateHolder certificateHolder = findSignerCertificate(cmsSignedData, signerInformation);
            if (certificateHolder != null) {
                response.setSignerSubject(certificateHolder.getSubject().toString());
                boolean signatureValid = signerInformation.verify(
                        new JcaSimpleSignerInfoVerifierBuilder().build(certificateHolder)
                );
                response.setCmsSignatureValid(signatureValid);
            }

            boolean digestOk = cmsDigest == null || response.isMessageDigestMatch();
            response.setValid(response.isByteRangeValid() && response.isCmsSignatureValid() && digestOk);
            return response;
        } catch (Exception ex) {
            response.setErrorMessage(ex.getMessage());
            response.setValid(false);
            return response;
        }
    }

    private String resolveHashAlgorithm(String requestHashAlg) {
        if (requestHashAlg == null || requestHashAlg.isBlank()) {
            return "SHA-256";
        }

        String normalized = requestHashAlg.toUpperCase(Locale.ROOT).replace("-", "");
        return switch (normalized) {
            case "SHA1" -> "SHA-1";
            case "SHA256" -> "SHA-256";
            case "SHA512" -> "SHA-512";
            default -> throw new IllegalArgumentException("Unsupported hashalg: " + requestHashAlg);
        };
    }

    private boolean isVisibleSignature(Integer typeSignature) {
        int type = typeSignature == null ? TYPE_SIGNATURE_INVISIBLE : typeSignature;
        return type == TYPE_SIGNATURE_TEXT || type == TYPE_SIGNATURE_IMAGE || type == TYPE_SIGNATURE_IMAGE_TEXT;
    }

    private void applyVisibleSignatureAppearance(PDDocument document, PDSignature signature, PreparePdfRequest request) throws IOException {
        PDSignatureField signatureField = findSignatureField(document, signature);
        if (signatureField == null) {
            throw new IllegalStateException("Could not locate signature field for appearance rendering");
        }

        int type = request.getTypesignature() == null ? TYPE_SIGNATURE_INVISIBLE : request.getTypesignature();
        if ((type == TYPE_SIGNATURE_IMAGE || type == TYPE_SIGNATURE_IMAGE_TEXT)
                && (request.getBase64image() == null || request.getBase64image().isBlank())) {
            throw new IllegalArgumentException("base64image is required for typesignature=2 or 3");
        }

        PDAnnotationWidget widget = signatureField.getWidgets().isEmpty()
                ? new PDAnnotationWidget()
                : signatureField.getWidgets().get(0);

        int pageIndex = resolvePageIndex(request.getPagesign(), document.getNumberOfPages());
        PDPage page = document.getPage(pageIndex);

        float x = request.getXpoint() == null ? DEFAULT_SIGNATURE_X : request.getXpoint();
        float y = request.getYpoint() == null ? DEFAULT_SIGNATURE_Y : request.getYpoint();
        float width = request.getWidth() == null ? DEFAULT_SIGNATURE_WIDTH : request.getWidth();
        float height = request.getHeight() == null ? DEFAULT_SIGNATURE_HEIGHT : request.getHeight();

        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Signature rectangle width/height must be greater than 0");
        }

        PDRectangle rect = new PDRectangle(x, y, width, height);
        widget.setRectangle(rect);
        widget.setPage(page);
        if (!page.getAnnotations().contains(widget)) {
            page.getAnnotations().add(widget);
        }

        String uniqueFieldName = buildUniqueSignatureFieldName(document, request.getSignaturename(), signatureField.getPartialName());
        if (uniqueFieldName != null) {
            signatureField.setPartialName(uniqueFieldName);
        }
        widget.setPrinted(true);

        PDAppearanceStream appearanceStream = new PDAppearanceStream(document);
        appearanceStream.setResources(new PDResources());
        appearanceStream.setBBox(new PDRectangle(width, height));

        renderAppearanceStream(document, appearanceStream, request, type, width, height);

        PDAppearanceDictionary appearanceDictionary = new PDAppearanceDictionary();
        appearanceDictionary.setNormalAppearance(appearanceStream);
        widget.setAppearance(appearanceDictionary);
    }

    private PDSignatureField findSignatureField(PDDocument document, PDSignature signature) throws IOException {
        PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
        if (acroForm == null) {
            return null;
        }

        for (PDField field : acroForm.getFieldTree()) {
            if (field instanceof PDSignatureField sigField) {
                PDSignature sig = sigField.getSignature();
                if (sig != null && sig.getCOSObject() == signature.getCOSObject()) {
                    return sigField;
                }
            }
        }
        return null;
    }

    private String buildUniqueSignatureFieldName(PDDocument document, String requestedName, String fallbackName) throws IOException {
        String baseName = requestedName == null || requestedName.isBlank() ? fallbackName : requestedName.trim();
        if (baseName == null || baseName.isBlank()) {
            return null;
        }

        PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
        if (acroForm == null) {
            return baseName;
        }

        java.util.Set<String> existingNames = new java.util.HashSet<>();
        for (PDField field : acroForm.getFieldTree()) {
            String name = field.getFullyQualifiedName();
            if (name != null && !name.isBlank()) {
                existingNames.add(name);
            }
        }

        if (!existingNames.contains(baseName)) {
            return baseName;
        }

        int i = 2;
        while (true) {
            String candidate = baseName + "_" + i;
            if (!existingNames.contains(candidate)) {
                return candidate;
            }
            i++;
        }
    }

    private int resolvePageIndex(Integer pageSign, int totalPages) {
        if (totalPages <= 0) {
            throw new IllegalArgumentException("PDF has no pages");
        }

        if (pageSign == null) {
            return totalPages - 1;
        }

        int page = pageSign;
        if (page < 1 || page > totalPages) {
            throw new IllegalArgumentException("pagesign is out of range, valid range is 1.." + totalPages);
        }
        return page - 1;
    }

    private void renderAppearanceStream(
            PDDocument document,
            PDAppearanceStream appearanceStream,
            PreparePdfRequest request,
            int type,
            float width,
            float height
    ) throws IOException {
        try (PDPageContentStream contentStream = new PDPageContentStream(document, appearanceStream)) {
            contentStream.setNonStrokingColor(1f, 1f, 1f);
            contentStream.addRect(0, 0, width, height);
            contentStream.fill();

            boolean drawImage = type == TYPE_SIGNATURE_IMAGE || type == TYPE_SIGNATURE_IMAGE_TEXT;
            boolean drawText = type == TYPE_SIGNATURE_TEXT || type == TYPE_SIGNATURE_IMAGE_TEXT;

            if (drawImage && request.getBase64image() != null && !request.getBase64image().isBlank()) {
                byte[] imageBytes = decodeBase64(request.getBase64image(), "base64image");
                PDImageXObject image = PDImageXObject.createFromByteArray(document, imageBytes, "signature-image");

                float imgMaxW = width - 10f;
                float imgMaxH = height - 10f;
                float ratio = Math.min(imgMaxW / image.getWidth(), imgMaxH / image.getHeight());
                float imgW = image.getWidth() * ratio;
                float imgH = image.getHeight() * ratio;
                float imgX = (width - imgW) / 2f;
                float imgY = (height - imgH) / 2f;
                contentStream.drawImage(image, imgX, imgY, imgW, imgH);
            }

            if (drawText) {
                String text = "Ký bởi: " + nonBlank(request.getTextout(), "Digitally signed") + "\n" +
                              "Ngày ký: " + java.time.LocalDateTime.now(java.time.ZoneId.systemDefault())
                                              .format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
                float textX = 8f;
                float textWidth = width - 16f;

                PDFont font;
                try (InputStream fontStream = new ByteArrayInputStream(ARIAL_FONT_BYTES)) {
                    font = PDType0Font.load(document, fontStream);
                }

                float textY = (type == TYPE_SIGNATURE_IMAGE_TEXT) ? 16f : (height / 2f) + 2f;
                drawTextLines(contentStream, font, text, textX, textY, textWidth);
            }
        }
    }

    private void drawTextLines(PDPageContentStream contentStream, PDFont font, String text, float x, float y, float maxWidth) throws IOException {
        float fontSize = 9f;
        float lineHeight = 12f;
        int maxCharsPerLine = Math.max(1, (int) (maxWidth / 5.2f));

        String normalized = text.replace("\r", "");
        String[] rawLines = normalized.split("\n");

        contentStream.setNonStrokingColor(0.0f, 0.5f, 0.0f);
        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.newLineAtOffset(x, y);

        int renderedLines = 0;
        for (String rawLine : rawLines) {
            String line = rawLine;
            while (!line.isEmpty()) {
                String chunk = line.length() > maxCharsPerLine ? line.substring(0, maxCharsPerLine) : line;
                contentStream.showText(chunk);
                renderedLines++;
                if (renderedLines >= 5) {
                    contentStream.endText();
                    return;
                }
                contentStream.newLineAtOffset(0, -lineHeight);
                line = line.length() > maxCharsPerLine ? line.substring(maxCharsPerLine) : "";
            }
        }

        contentStream.endText();
    }

    private String nonBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String mapDigestAlgorithmFromOid(String oid) {
        return switch (oid) {
            case "1.3.14.3.2.26" -> "SHA-1";
            case "2.16.840.1.101.3.4.2.1" -> "SHA-256";
            case "2.16.840.1.101.3.4.2.3" -> "SHA-512";
            default -> "SHA-256";
        };
    }

    private byte[] decodeBase64(String value, String fieldName) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid base64 in field: " + fieldName, ex);
        }
    }

    private byte[] injectSignature(byte[] preparedPdfBytes, byte[] signatureBytes) {
        long[] byteRange = readByteRange(preparedPdfBytes);
        ByteRangeInfo byteRangeInfo = parseByteRangeInfo(preparedPdfBytes, byteRange);

        if (preparedPdfBytes[byteRangeInfo.gapStart] != '<' || preparedPdfBytes[byteRangeInfo.gapEnd - 1] != '>') {
            throw new IllegalArgumentException("Prepared PDF does not contain an embeddable signature placeholder");
        }

        int hexCapacity = byteRangeInfo.gapEnd - byteRangeInfo.gapStart - 2;
        String signatureHex = toHex(signatureBytes);

        if (signatureHex.length() > hexCapacity) {
            throw new IllegalArgumentException(
                    "Signature too large for placeholder: need "
                            + signatureHex.length()
                            + " hex chars, capacity is "
                            + hexCapacity
            );
        }

        String paddedHex = signatureHex + "0".repeat(hexCapacity - signatureHex.length());
        byte[] paddedHexBytes = paddedHex.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(paddedHexBytes, 0, preparedPdfBytes, byteRangeInfo.gapStart + 1, paddedHexBytes.length);

        return preparedPdfBytes;
    }

    private ByteRangeInfo parseByteRangeInfo(byte[] pdfBytes) {
        return parseByteRangeInfo(pdfBytes, readByteRange(pdfBytes));
    }

    private ByteRangeInfo parseByteRangeInfo(byte[] pdfBytes, long[] byteRange) {
        int signedPart1Start = Math.toIntExact(byteRange[0]);
        int signedPart1Length = Math.toIntExact(byteRange[1]);
        int signedPart2Start = Math.toIntExact(byteRange[2]);
        int signedPart2Length = Math.toIntExact(byteRange[3]);
        int gapStart = signedPart1Start + signedPart1Length;
        int gapEnd = signedPart2Start;

        if (signedPart1Start != 0) {
            throw new IllegalArgumentException("Unsupported ByteRange: first offset must be 0");
        }

        if (signedPart1Length < 0 || signedPart2Length < 0 || gapStart < 0 || gapEnd <= gapStart) {
            throw new IllegalArgumentException("Invalid ByteRange offsets in PDF");
        }

        if (gapEnd > pdfBytes.length || signedPart2Start + signedPart2Length != pdfBytes.length) {
            throw new IllegalArgumentException("ByteRange does not match full PDF length");
        }

        return new ByteRangeInfo(gapStart, gapEnd, signedPart1Length, signedPart2Start, signedPart2Length);
    }

    private byte[] buildSignedContent(byte[] pdfBytes, ByteRangeInfo byteRangeInfo) {
        byte[] signedContent = new byte[byteRangeInfo.signedPart1Length + byteRangeInfo.signedPart2Length];
        System.arraycopy(pdfBytes, 0, signedContent, 0, byteRangeInfo.signedPart1Length);
        System.arraycopy(pdfBytes, byteRangeInfo.signedPart2Start, signedContent, byteRangeInfo.signedPart1Length, byteRangeInfo.signedPart2Length);
        return signedContent;
    }

    private byte[] extractSignatureBytes(byte[] pdfBytes, ByteRangeInfo byteRangeInfo) {
        if (pdfBytes[byteRangeInfo.gapStart] != '<' || pdfBytes[byteRangeInfo.gapEnd - 1] != '>') {
            throw new IllegalArgumentException("Could not find signature contents placeholder in PDF");
        }

        byte[] hexBytes = Arrays.copyOfRange(pdfBytes, byteRangeInfo.gapStart + 1, byteRangeInfo.gapEnd - 1);
        int trimmedLength = hexBytes.length;
        while (trimmedLength > 0 && hexBytes[trimmedLength - 1] == '0') {
            trimmedLength--;
        }

        if (trimmedLength <= 0) {
            throw new IllegalArgumentException("Embedded signature is empty");
        }

        if ((trimmedLength & 1) == 1) {
            trimmedLength--;
        }

        String hexSignature = new String(hexBytes, 0, trimmedLength, StandardCharsets.US_ASCII);
        return hexToBytes(hexSignature);
    }

    private byte[] extractMessageDigestFromSignedAttributes(SignerInformation signerInformation) {
        AttributeTable attrs = signerInformation.getSignedAttributes();
        if (attrs == null) {
            return null;
        }

        Attribute messageDigestAttr = attrs.get(PKCSObjectIdentifiers.pkcs_9_at_messageDigest);
        if (messageDigestAttr == null || messageDigestAttr.getAttrValues().size() == 0) {
            return null;
        }

        ASN1OctetString digest = (ASN1OctetString) messageDigestAttr.getAttrValues().getObjectAt(0);
        return digest.getOctets();
    }

    private X509CertificateHolder findSignerCertificate(CMSSignedData cmsSignedData, SignerInformation signerInformation) {
        Store<X509CertificateHolder> certificateStore = cmsSignedData.getCertificates();
        @SuppressWarnings("unchecked")
        Collection<X509CertificateHolder> certs = certificateStore.getMatches((org.bouncycastle.util.Selector<X509CertificateHolder>) signerInformation.getSID());
        if (certs.isEmpty()) {
            return null;
        }
        return certs.iterator().next();
    }

    private long[] readByteRange(byte[] pdfBytes) {
        byte[] target = "/ByteRange".getBytes(StandardCharsets.US_ASCII);
        int index = lastIndexOf(pdfBytes, target);
        if (index == -1) {
            throw new IllegalArgumentException("Could not find /ByteRange in prepared PDF");
        }
        return parseByteRangeValues(pdfBytes, index + target.length);
    }

    private int lastIndexOf(byte[] array, byte[] target) {
        if (target.length == 0) return 0;
        outer:
        for (int i = array.length - target.length; i >= 0; i--) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private long[] parseByteRangeValues(byte[] pdfBytes, int startOffset) {
        int i = startOffset;
        int len = pdfBytes.length;

        // 1. Skip whitespace until '['
        while (i < len && isWhitespace(pdfBytes[i])) {
            i++;
        }
        if (i >= len || pdfBytes[i] != '[') {
            throw new IllegalArgumentException("Could not find opening bracket '[' after /ByteRange");
        }
        i++; // Skip '['

        long[] result = new long[4];
        for (int numIdx = 0; numIdx < 4; numIdx++) {
            // Skip whitespace
            while (i < len && isWhitespace(pdfBytes[i])) {
                i++;
            }
            // Read digits
            if (i >= len || !isDigit(pdfBytes[i])) {
                throw new IllegalArgumentException("Expected digit in /ByteRange at offset " + i);
            }
            long val = 0;
            while (i < len && isDigit(pdfBytes[i])) {
                val = val * 10 + (pdfBytes[i] - '0');
                i++;
            }
            result[numIdx] = val;
        }

        // Skip whitespace until ']'
        while (i < len && isWhitespace(pdfBytes[i])) {
            i++;
        }
        if (i >= len || pdfBytes[i] != ']') {
            throw new IllegalArgumentException("Could not find closing bracket ']' in /ByteRange");
        }

        return result;
    }

    private boolean isWhitespace(byte b) {
        return b == ' ' || b == '\t' || b == '\r' || b == '\n' || b == '\f' || b == 0;
    }

    private boolean isDigit(byte b) {
        return b >= '0' && b <= '9';
    }

    private String toHex(byte[] bytes) {
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }

    private byte[] hexToBytes(String hex) {
        try {
            return HexFormat.of().parseHex(hex);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Embedded signature contains non-hex characters", ex);
        }
    }

    private static class ByteRangeInfo {
        private final int gapStart;
        private final int gapEnd;
        private final int signedPart1Length;
        private final int signedPart2Start;
        private final int signedPart2Length;

        private ByteRangeInfo(int gapStart, int gapEnd, int signedPart1Length, int signedPart2Start, int signedPart2Length) {
            this.gapStart = gapStart;
            this.gapEnd = gapEnd;
            this.signedPart1Length = signedPart1Length;
            this.signedPart2Start = signedPart2Start;
            this.signedPart2Length = signedPart2Length;
        }
    }
}