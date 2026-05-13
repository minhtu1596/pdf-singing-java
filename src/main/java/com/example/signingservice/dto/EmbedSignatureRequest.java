package com.example.signingservice.dto;

public class EmbedSignatureRequest {

    private String preparedPdfBase64;

    private String signatureBase64;

    public String getPreparedPdfBase64() {
        return preparedPdfBase64;
    }

    public void setPreparedPdfBase64(String preparedPdfBase64) {
        this.preparedPdfBase64 = preparedPdfBase64;
    }

    public String getSignatureBase64() {
        return signatureBase64;
    }

    public void setSignatureBase64(String signatureBase64) {
        this.signatureBase64 = signatureBase64;
    }
}