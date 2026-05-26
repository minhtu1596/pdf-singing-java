# Signing Service (Java)

Spring Boot service for 2-step external PDF signing:

1. `POST /prepare-pdf` creates signature placeholder and returns:
   - `preparedPdfBase64`
   - `hashBase64` (SHA-256 of PDF byte ranges to be signed)
2. `POST /embed-signature` injects CMS/PKCS#7 signature bytes into the prepared PDF placeholder and returns:
   - `signedPdfBase64`
   - `savedFilePath` (server-side debug file path)
3. `POST /verify-signature` validates a signed PDF and returns ByteRange/CMS diagnostics.

All endpoints now return this common envelope:

```json
{
  "success": true,
  "message": "...",
  "code": "SUCCESS",
  "data": {}
}
```

## API payloads

### Prepare

Request:

```json
{
  "pdfBase64": "<base64-pdf>"
}
```

Response:

```json
{
  "success": true,
  "message": "Prepare PDF successfully",
  "code": "SUCCESS",
  "data": {
    "preparedPdfBase64": "<base64-prepared-pdf>",
    "hashBase64": "<base64-hash>",
    "hashAlgorithm": "SHA-256"
  }
}
```

Optional request fields (Cyber-compatible):

- `hashalg`: `SHA1` | `SHA256` | `SHA512` (default `SHA256`)
- `typesignature`: `0` invisible, `1` text, `2` image, `3` image+text
- `signaturename`: signature field/display name
- `base64image`: image for visible signature
- `textout`: visible text content
- `pagesign`: 1-based page number
- `xpoint`, `ypoint`, `width`, `height`: signature rectangle

### Embed

Request:

```json
{
  "preparedPdfBase64": "<base64-prepared-pdf>",
  "signatureBase64": "<base64-cms-pkcs7-signature>"
}
```

Response:

```json
{
  "success": true,
  "message": "Embed signature successfully",
  "code": "SUCCESS",
  "data": {
    "signedPdfBase64": "<base64-signed-pdf>",
    "savedFilePath": "<absolute-path-on-java-server>"
  }
}
```

### Verify

Request:

```json
{
  "signedPdfBase64": "<base64-signed-pdf>"
}
```

Response:

```json
{
  "success": true,
  "message": "Verify signature completed",
  "code": "SUCCESS",
  "data": {
    "valid": true,
    "byteRangeValid": true,
    "cmsParsed": true,
    "cmsSignatureValid": true,
    "messageDigestMatch": true,
    "signedContentHashBase64": "<base64-sha256>",
    "cmsMessageDigestBase64": "<base64-from-cms-signed-attributes>",
    "signerSubject": "CN=...",
    "errorMessage": null
  }
}
```

Error example:

```json
{
  "success": false,
  "message": "Invalid base64 in field: pdfBase64",
  "code": "INVALID_REQUEST",
  "data": null
}
```

## Run and test

```bash
cd /Users/tech1/Downloads/signingservice
./mvnw test
./mvnw spring-boot:run
```



