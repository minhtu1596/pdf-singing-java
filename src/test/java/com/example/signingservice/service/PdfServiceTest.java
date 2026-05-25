package com.example.signingservice.service;

import com.example.signingservice.dto.EmbedSignatureResponse;
import com.example.signingservice.dto.PreparePdfResponse;
import com.example.signingservice.dto.VerifySignatureResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfServiceTest {

    private static final String SAMPLE_PDF_BASE64 =
            "JVBERi0xLjQKMSAwIG9iajw8L1R5cGUvQ2F0YWxvZy9QYWdlcyAyIDAgUj4+ZW5kb2JqCjIgMCBvYmo8PC9UeXBlL1BhZ2VzL0tpZHNbMyAwIFJdL0NvdW50IDE+PmVuZG9iagozIDAgb2JqPDwvVHlwZS9QYWdlL01lZGlhQm94WzAgMCA2MTIgNzkyXS9QYXJlbnQgMiAwIFIvUmVzb3VyY2VzPDwvRm9udDw8L0YxIDQgMCBSPj4+Pi9Db250ZW50cyA1IDAgUj4+ZW5kb2JqCjQgMCBvYmo8PC9UeXBlL0ZvbnQvU3VidHlwZS9UeXBlMS9CYXNlRm9udC9UaW1lcy1Sb21hbj4+ZW5kb2JqCjUgMCBvYmo8PC9MZW5ndGggNDQ+PgpzdHJlYW0KQlQgL0YxIDEyIFRmIDEwMCA3MDAgVGQgKFRlc3QgZVNpZ24pIFRqIEVUCmVuZHN0cmVhbQplbmRvYmoKeHJlZgowIDYKMDAwMDAwMDAwMCA2NTUzNSBmCjAwMDAwMDAwMDkgMDAwMDAgbgowMDAwMDAwMDUyIDAwMDAwIG4KMDAwMDAwMDEwMSAwMDAwMCBuCjAwMDAwMDAyMTcgMDAwMDAgbgowMDAwMDAwMjg0IDAwMDAwIG4KdHJhaWxlcjw8L1NpemUgNi9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM3NgolJUVPRg==";

    @Test
    void shouldPrepareAndEmbedSignature() throws Exception {
        PdfService service = new PdfService();

        PreparePdfResponse prepared = service.preparePdf(SAMPLE_PDF_BASE64);
        assertNotNull(prepared.getPreparedPdfBase64());
        assertNotNull(prepared.getHashBase64());
        assertFalse(prepared.getPreparedPdfBase64().isBlank());
        assertFalse(prepared.getHashBase64().isBlank());

        byte[] cmsSignature = new byte[512];
        for (int i = 0; i < cmsSignature.length; i++) {
            cmsSignature[i] = (byte) (i % 256);
        }

        EmbedSignatureResponse signed = service.embedSignature(
                prepared.getPreparedPdfBase64(),
                Base64.getEncoder().encodeToString(cmsSignature)
        );

        assertNotNull(signed.getSignedPdfBase64());
        assertFalse(signed.getSignedPdfBase64().isBlank());
        assertNotEquals(prepared.getPreparedPdfBase64(), signed.getSignedPdfBase64());
        assertNotNull(signed.getSavedFilePath());
        assertFalse(signed.getSavedFilePath().isBlank());
        assertTrue(Files.exists(Path.of(signed.getSavedFilePath())));

        String signedPdfText = new String(Base64.getDecoder().decode(signed.getSignedPdfBase64()));
        assertTrue(signedPdfText.contains("/ByteRange"));

        byte[] signedPdfBytes = Base64.getDecoder().decode(signed.getSignedPdfBase64());
        assertDoesNotThrow(() -> {
            try (PDDocument ignored = Loader.loadPDF(signedPdfBytes)) {
                assertTrue(ignored.getNumberOfPages() > 0);
            }
        });
    }

    @Test
    void shouldVerifyAndReturnDiagnosticsForSignedPdf() throws Exception {
        PdfService service = new PdfService();

        PreparePdfResponse prepared = service.preparePdf(SAMPLE_PDF_BASE64);
        byte[] cmsSignature = new byte[512];
        for (int i = 0; i < cmsSignature.length; i++) {
            cmsSignature[i] = (byte) (i % 256);
        }

        EmbedSignatureResponse signed = service.embedSignature(
                prepared.getPreparedPdfBase64(),
                Base64.getEncoder().encodeToString(cmsSignature)
        );

        VerifySignatureResponse verify = service.verifySignature(signed.getSignedPdfBase64());

        assertNotNull(verify);
        assertTrue(verify.isByteRangeValid());
        assertNotNull(verify.getSignedContentHashBase64());
        assertFalse(verify.isValid());
    }
}





