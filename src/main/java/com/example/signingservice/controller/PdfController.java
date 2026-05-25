package com.example.signingservice.controller;

import com.example.signingservice.dto.PreparePdfRequest;
import com.example.signingservice.dto.PreparePdfResponse;
import com.example.signingservice.service.PdfService;
import org.springframework.web.bind.annotation.*;
import com.example.signingservice.dto.EmbedSignatureRequest;
import com.example.signingservice.dto.EmbedSignatureResponse;
import com.example.signingservice.dto.VerifySignatureRequest;
import com.example.signingservice.dto.VerifySignatureResponse;

@RestController
public class PdfController {

    private final PdfService pdfService;

    public PdfController(PdfService pdfService) {
        this.pdfService = pdfService;
    }

    @PostMapping("/prepare-pdf")
    public PreparePdfResponse preparePdf(
            @RequestBody PreparePdfRequest request
    ) throws Exception {

        return pdfService.preparePdf(
                request.getPdfBase64()
        );
    }

    @PostMapping("/embed-signature")
    public EmbedSignatureResponse embedSignature(
            @RequestBody EmbedSignatureRequest request
    ) {

        return pdfService.embedSignature(
                request.getPreparedPdfBase64(),
                request.getSignatureBase64()
        );
    }

    @PostMapping("/verify-signature")
    public VerifySignatureResponse verifySignature(
            @RequestBody VerifySignatureRequest request
    ) {

        return pdfService.verifySignature(
                request.getSignedPdfBase64()
        );
    }
}