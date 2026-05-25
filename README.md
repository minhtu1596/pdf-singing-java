# Signing Service (Java)

Spring Boot service for 2-step external PDF signing:

1. `POST /prepare-pdf` creates signature placeholder and returns:
   - `preparedPdfBase64`
   - `hashBase64` (SHA-256 of PDF byte ranges to be signed)
2. `POST /embed-signature` injects CMS/PKCS#7 signature bytes into the prepared PDF placeholder and returns:
   - `signedPdfBase64`
   - `savedFilePath` (server-side debug file path)
3. `POST /verify-signature` validates a signed PDF and returns ByteRange/CMS diagnostics.

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
  "preparedPdfBase64": "<base64-prepared-pdf>",
  "hashBase64": "<base64-sha256-hash>"
}
```

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
  "signedPdfBase64": "<base64-signed-pdf>",
  "savedFilePath": "<absolute-path-on-java-server>"
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
```

## Run and test

```bash
cd /Users/tech1/Downloads/signingservice
./mvnw test
./mvnw spring-boot:run
```



