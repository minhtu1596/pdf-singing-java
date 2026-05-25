package com.example.signingservice.dto;

public class VerifySignatureResponse {

    private boolean valid;

    private boolean byteRangeValid;

    private boolean cmsParsed;

    private boolean cmsSignatureValid;

    private boolean messageDigestMatch;

    private String signedContentHashBase64;

    private String cmsMessageDigestBase64;

    private String signerSubject;

    private String errorMessage;

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public boolean isByteRangeValid() {
        return byteRangeValid;
    }

    public void setByteRangeValid(boolean byteRangeValid) {
        this.byteRangeValid = byteRangeValid;
    }

    public boolean isCmsParsed() {
        return cmsParsed;
    }

    public void setCmsParsed(boolean cmsParsed) {
        this.cmsParsed = cmsParsed;
    }

    public boolean isCmsSignatureValid() {
        return cmsSignatureValid;
    }

    public void setCmsSignatureValid(boolean cmsSignatureValid) {
        this.cmsSignatureValid = cmsSignatureValid;
    }

    public boolean isMessageDigestMatch() {
        return messageDigestMatch;
    }

    public void setMessageDigestMatch(boolean messageDigestMatch) {
        this.messageDigestMatch = messageDigestMatch;
    }

    public String getSignedContentHashBase64() {
        return signedContentHashBase64;
    }

    public void setSignedContentHashBase64(String signedContentHashBase64) {
        this.signedContentHashBase64 = signedContentHashBase64;
    }

    public String getCmsMessageDigestBase64() {
        return cmsMessageDigestBase64;
    }

    public void setCmsMessageDigestBase64(String cmsMessageDigestBase64) {
        this.cmsMessageDigestBase64 = cmsMessageDigestBase64;
    }

    public String getSignerSubject() {
        return signerSubject;
    }

    public void setSignerSubject(String signerSubject) {
        this.signerSubject = signerSubject;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}

