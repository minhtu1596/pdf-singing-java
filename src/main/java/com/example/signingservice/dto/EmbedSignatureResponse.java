package com.example.signingservice.dto;

public class EmbedSignatureResponse {

    private String signedPdfBase64;

    public String getSignedPdfBase64() {
        return signedPdfBase64;
    }

    public void setSignedPdfBase64(String signedPdfBase64) {
        this.signedPdfBase64 = signedPdfBase64;
    }
}