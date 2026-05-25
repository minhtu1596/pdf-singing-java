# PHP -> Java Signing Integration Guide

This document explains exactly what PHP must send to Java in each step.

## 1) Call Java `/prepare-pdf`

### Request JSON

```json
{
  "pdfBase64": "<base64 of original PDF bytes>",
  "hashalg": "SHA256",
  "typesignature": 0,
  "signaturename": "Signature1",
  "base64image": "<optional>",
  "textout": "<optional>",
  "pagesign": 1,
  "xpoint": 50,
  "ypoint": 50,
  "width": 200,
  "height": 60
}
```

- `pdfBase64`: base64 encode of full PDF file bytes.

### Response JSON

```json
{
  "preparedPdfBase64": "<base64 of PDF with signature placeholder>",
  "hashBase64": "<base64 hash to sign>",
  "hashAlgorithm": "SHA-256"
}
```

- `preparedPdfBase64`: temporary PDF containing placeholder + ByteRange
- `hashBase64`: exact hash bytes that HSM must sign

## 2) Send hash to HSM/CyberSign

HSM input should be decoded hash bytes from `hashBase64`.

HSM output should be **CMS/PKCS#7 detached signature in DER bytes**.

Then PHP must convert those signature bytes to base64 as `signatureBase64`.

> Do NOT send hex text to Java.
> Java expects base64(raw CMS bytes), not base64(hex-string).

## 3) Call Java `/embed-signature`

### Request JSON

```json
{
  "preparedPdfBase64": "<from step 1 response>",
  "signatureBase64": "<base64 CMS/PKCS#7 DER bytes from HSM>"
}
```

### Response JSON

```json
{
  "signedPdfBase64": "<base64 final signed PDF>",
  "savedFilePath": "<absolute-path-on-java-server>"
}
```

PHP decodes `signedPdfBase64` and writes bytes to output PDF file.

`savedFilePath` is a debug convenience path written by Java server to help quick manual testing.

## 4) Optional debug: call Java `/verify-signature`

If Adobe still reports issues, verify the produced signed PDF:

```json
{
  "signedPdfBase64": "<base64 final signed PDF>"
}
```

Look at these fields in response:
- `byteRangeValid`
- `cmsParsed`
- `cmsSignatureValid`
- `messageDigestMatch`
- `errorMessage`

## Minimal cURL examples

### Prepare PDF

```bash
curl -X POST http://127.0.0.1:8080/prepare-pdf \
  -H 'Content-Type: application/json' \
  -d '{"pdfBase64":"<base64-pdf>"}'
```

### Embed signature

```bash
curl -X POST http://127.0.0.1:8080/embed-signature \
  -H 'Content-Type: application/json' \
  -d '{"preparedPdfBase64":"<prepared>","signatureBase64":"<cms-base64>"}'
```

## Error checklist

- `Invalid base64 in field ...`: PHP sent malformed base64 text
- `Could not find /ByteRange`: step 2 used wrong PDF (not from `/prepare-pdf`)
- `Signature too large for placeholder`: increase Java placeholder size

## Files in this folder

- `examples/php-signing-client.php`: end-to-end PHP sample client (with HSM stub)



