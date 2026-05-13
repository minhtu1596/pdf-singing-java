package com.example.signingservice.dto;

public class PreparePdfResponse {

    private String preparedPdfBase64;

    private String hashBase64;

    public String getPreparedPdfBase64() {
        return preparedPdfBase64;
    }

    public void setPreparedPdfBase64(String preparedPdfBase64) {
        this.preparedPdfBase64 = preparedPdfBase64;
    }

    public String getHashBase64() {
        return hashBase64;
    }

    public void setHashBase64(String hashBase64) {
        this.hashBase64 = hashBase64;
    }
}