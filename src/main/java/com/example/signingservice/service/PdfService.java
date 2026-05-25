package com.example.signingservice.service;

import com.example.signingservice.dto.EmbedSignatureResponse;
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
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfService {

    private static final int DEFAULT_SIGNATURE_PLACEHOLDER_SIZE = 16_384;

    private static final Path DEBUG_SIGNED_PDF_DIR = Path.of("target", "signed-debug");

    private static final Pattern BYTE_RANGE_PATTERN =
            Pattern.compile("/ByteRange\\s*\\[\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*]");

    public PreparePdfResponse preparePdf(
            String pdfBase64
    ) throws Exception {

        byte[] pdfBytes = decodeBase64(pdfBase64, "pdfBase64");
        byte[] normalizedPdfBytes = normalizePdfForSigning(pdfBytes);

        try (
                PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(normalizedPdfBytes));
//                PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes));
                SignatureOptions signatureOptions = new SignatureOptions();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        ) {
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName("External Signing");
            signature.setReason("Document signed");
            signature.setSignDate(java.util.GregorianCalendar.from(Instant.now().atZone(java.time.ZoneId.systemDefault())));

            signatureOptions.setPreferredSignatureSize(DEFAULT_SIGNATURE_PLACEHOLDER_SIZE);
            document.addSignature(signature, signatureOptions);

            ExternalSigningSupport externalSigning =
                    document.saveIncrementalForExternalSigning(outputStream);

            byte[] contentToSign = externalSigning.getContent().readAllBytes();
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(contentToSign);

            // Keep the reserved /Contents gap open by writing zero bytes as placeholder.
            externalSigning.setSignature(new byte[DEFAULT_SIGNATURE_PLACEHOLDER_SIZE]);

            PreparePdfResponse response = new PreparePdfResponse();
            response.setPreparedPdfBase64(Base64.getEncoder().encodeToString(outputStream.toByteArray()));
            response.setHashBase64(Base64.getEncoder().encodeToString(hash));
            return response;
        }
    }

    private byte[] normalizePdfForSigning(byte[] pdfBytes) {
        try (
                PDDocument source = Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes));
                ByteArrayOutputStream normalizedOutput = new ByteArrayOutputStream()
        ) {
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
        String savedFilePath = saveSignedPdfForDebug(signedPdfBytes);

        EmbedSignatureResponse response = new EmbedSignatureResponse();
        response.setSignedPdfBase64(Base64.getEncoder().encodeToString(signedPdfBytes));
        response.setSavedFilePath(savedFilePath);
        return response;
    }

    public VerifySignatureResponse verifySignature(String signedPdfBase64) {
        VerifySignatureResponse response = new VerifySignatureResponse();

        try {
            byte[] signedPdfBytes = decodeBase64(signedPdfBase64, "signedPdfBase64");
            ByteRangeInfo byteRangeInfo = parseByteRangeInfo(signedPdfBytes);
            response.setByteRangeValid(true);

            byte[] signedContent = buildSignedContent(signedPdfBytes, byteRangeInfo);
            byte[] signedContentHash = MessageDigest.getInstance("SHA-256").digest(signedContent);
            response.setSignedContentHashBase64(Base64.getEncoder().encodeToString(signedContentHash));

            byte[] signatureBytes = extractSignatureBytes(signedPdfBytes, byteRangeInfo);
            CMSSignedData cmsSignedData = new CMSSignedData(new CMSProcessableByteArray(signedContent), signatureBytes);
            response.setCmsParsed(true);

            SignerInformationStore signerInfos = cmsSignedData.getSignerInfos();
            if (signerInfos.getSigners().isEmpty()) {
                throw new IllegalArgumentException("No signer info found in CMS signature");
            }

            SignerInformation signerInformation = signerInfos.getSigners().iterator().next();
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

    private String saveSignedPdfForDebug(byte[] signedPdfBytes) {
        try {
            Files.createDirectories(DEBUG_SIGNED_PDF_DIR);
            String filename = "signed-" + System.currentTimeMillis() + ".pdf";
            Path outputPath = DEBUG_SIGNED_PDF_DIR.resolve(filename).toAbsolutePath().normalize();
            Files.write(outputPath, signedPdfBytes);
            return outputPath.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not save signed PDF for debug", ex);
        }
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
        byte[] signedPdf = preparedPdfBytes.clone();
        byte[] paddedHexBytes = paddedHex.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(paddedHexBytes, 0, signedPdf, byteRangeInfo.gapStart + 1, paddedHexBytes.length);

        return signedPdf;
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
        Collection<X509CertificateHolder> certs = certificateStore.getMatches(signerInformation.getSID());
        if (certs.isEmpty()) {
            return null;
        }
        return certs.iterator().next();
    }

    private long[] readByteRange(byte[] pdfBytes) {
        // PDF tokens are ASCII-compatible; ISO_8859_1 preserves byte indexes 1:1.
        String pdfText = new String(pdfBytes, StandardCharsets.ISO_8859_1);
        Matcher matcher = BYTE_RANGE_PATTERN.matcher(pdfText);

        long[] result = null;
        while (matcher.find()) {
            result = new long[4];
            for (int i = 0; i < 4; i++) {
                result[i] = Long.parseLong(matcher.group(i + 1));
            }
        }

        if (result == null) {
            throw new IllegalArgumentException("Could not find /ByteRange in prepared PDF");
        }

        return result;
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            sb.append(String.format("%02X", value));
        }
        return sb.toString();
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        if ((len & 1) == 1) {
            throw new IllegalArgumentException("Invalid hex signature length");
        }

        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Embedded signature contains non-hex characters");
            }
            data[i / 2] = (byte) ((hi << 4) + lo);
        }
        return data;
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