package com.example.signingservice.dto;

public class PreparePdfRequest {

    private String pdfBase64;

    // SHA1 | SHA256 | SHA512 (default: SHA256)
    private String hashalg;

    // 0=invisible, 1=text, 2=image, 3=image+text
    private Integer typesignature;

    private String signaturename;

    private String base64image;

    private String textout;

    // 1-based page number
    private Integer pagesign;

    private Integer xpoint;

    private Integer ypoint;

    private Integer width;

    private Integer height;

    public String getPdfBase64() {
        return pdfBase64;
    }

    public void setPdfBase64(String pdfBase64) {
        this.pdfBase64 = pdfBase64;
    }

    public String getHashalg() {
        return hashalg;
    }

    public void setHashalg(String hashalg) {
        this.hashalg = hashalg;
    }

    public Integer getTypesignature() {
        return typesignature;
    }

    public void setTypesignature(Integer typesignature) {
        this.typesignature = typesignature;
    }

    public String getSignaturename() {
        return signaturename;
    }

    public void setSignaturename(String signaturename) {
        this.signaturename = signaturename;
    }

    public String getBase64image() {
        return base64image;
    }

    public void setBase64image(String base64image) {
        this.base64image = base64image;
    }

    public String getTextout() {
        return textout;
    }

    public void setTextout(String textout) {
        this.textout = textout;
    }

    public Integer getPagesign() {
        return pagesign;
    }

    public void setPagesign(Integer pagesign) {
        this.pagesign = pagesign;
    }

    public Integer getXpoint() {
        return xpoint;
    }

    public void setXpoint(Integer xpoint) {
        this.xpoint = xpoint;
    }

    public Integer getYpoint() {
        return ypoint;
    }

    public void setYpoint(Integer ypoint) {
        this.ypoint = ypoint;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }
}