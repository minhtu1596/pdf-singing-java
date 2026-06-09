package com.example.signingservice.dto;

public class PreparePdfResponse {

    private String preparedPdfBase64;

    private String hashBase64;

    private String hashAlgorithm;

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

    public String getHashAlgorithm() {
        return hashAlgorithm;
        //test
    }

    public void setHashAlgorithm(String hashAlgorithm) {
        this.hashAlgorithm = hashAlgorithm;
    }

    private String test_cicd;

    public String getTest_cicd() {
        return test_cicd;
    }

    public void setTest_cicd(String test_cicd) {
        this.test_cicd = test_cicd;
    }
}