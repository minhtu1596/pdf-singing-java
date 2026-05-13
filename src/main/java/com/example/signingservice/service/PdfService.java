package com.example.signingservice.service;

import com.example.signingservice.dto.PreparePdfResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.springframework.stereotype.Service;
import com.example.signingservice.dto.EmbedSignatureRequest;
import com.example.signingservice.dto.EmbedSignatureResponse;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class PdfService {

    public PreparePdfResponse preparePdf(
            String pdfBase64
    ) throws Exception {

        byte[] pdfBytes =
                Base64.getDecoder()
                        .decode(pdfBase64);

        PDDocument document =
                Loader.loadPDF(
                        new RandomAccessReadBuffer(pdfBytes)
                );

        PDSignature signature =
                new PDSignature();

        signature.setFilter(
                PDSignature.FILTER_ADOBE_PPKLITE
        );

        signature.setSubFilter(
                PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED
        );

        signature.setName("External Signing");

        signature.setReason("Document signed");

        signature.setLocation("Vietnam");

        document.addSignature(signature);

        ByteArrayOutputStream outputStream =
                new ByteArrayOutputStream();

        ExternalSigningSupport externalSigning =
                document.saveIncrementalForExternalSigning(
                        outputStream
                );

        byte[] contentToSign =
                externalSigning
                        .getContent()
                        .readAllBytes();

        MessageDigest md =
                MessageDigest.getInstance("SHA-256");

        byte[] hash =
                md.digest(contentToSign);

        // fake signature placeholder
        byte[] fakeSignature = new byte[8192];

        externalSigning.setSignature(fakeSignature);

        document.close();

        PreparePdfResponse response =
                new PreparePdfResponse();

        response.setPreparedPdfBase64(
                Base64.getEncoder()
                        .encodeToString(
                                outputStream.toByteArray()
                        )
        );

        response.setHashBase64(
                Base64.getEncoder()
                        .encodeToString(hash)
        );

        return response;
    }

    public EmbedSignatureResponse embedSignature(
            String preparedPdfBase64,
            String signatureBase64
    ) throws Exception {

        byte[] preparedPdfBytes =
                Base64.getDecoder()
                        .decode(preparedPdfBase64);

        byte[] signatureBytes =
                Base64.getDecoder()
                        .decode(signatureBase64);

        PDDocument document =
                Loader.loadPDF(
                        new RandomAccessReadBuffer(preparedPdfBytes)
                );

        ByteArrayOutputStream outputStream =
                new ByteArrayOutputStream();

        ExternalSigningSupport externalSigning =
                document.saveIncrementalForExternalSigning(
                        outputStream
                );

        externalSigning.setSignature(signatureBytes);

        document.close();

        EmbedSignatureResponse response =
                new EmbedSignatureResponse();

        response.setSignedPdfBase64(
                Base64.getEncoder()
                        .encodeToString(
                                outputStream.toByteArray()
                        )
        );

        return response;
    }
}