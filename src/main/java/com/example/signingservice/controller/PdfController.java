package com.example.signingservice.controller;

import com.example.signingservice.dto.ApiResponse;
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
    public ApiResponse<PreparePdfResponse> preparePdf(
            @RequestBody PreparePdfRequest request
    ) throws Exception {

        PreparePdfResponse data = pdfService.preparePdf(request);
        return ApiResponse.success("Prepare PDF successfully", data);
    }

    @PostMapping("/embed-signature")
    public ApiResponse<EmbedSignatureResponse> embedSignature(
            @RequestBody EmbedSignatureRequest request
    ) {

        EmbedSignatureResponse data = pdfService.embedSignature(
                request.getPreparedPdfBase64(),
                request.getSignatureBase64()
        );
        return ApiResponse.success("Embed signature successfully", data);
    }

    @PostMapping("/verify-signature")
    public ApiResponse<VerifySignatureResponse> verifySignature(
            @RequestBody VerifySignatureRequest request
    ) {

        VerifySignatureResponse data = pdfService.verifySignature(
                request.getSignedPdfBase64()
        );
        return ApiResponse.success("Verify signature completed", data);
    }
}