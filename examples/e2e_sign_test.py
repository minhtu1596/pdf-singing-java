import base64
import json
import urllib.request

PDF_B64 = "JVBERi0xLjQKMSAwIG9iajw8L1R5cGUvQ2F0YWxvZy9QYWdlcyAyIDAgUj4+ZW5kb2JqCjIgMCBvYmo8PC9UeXBlL1BhZ2VzL0tpZHNbMyAwIFJdL0NvdW50IDE+PmVuZG9iagozIDAgb2JqPDwvVHlwZS9QYWdlL01lZGlhQm94WzAgMCA2MTIgNzkyXS9QYXJlbnQgMiAwIFIvUmVzb3VyY2VzPDwvRm9udDw8L0YxIDQgMCBSPj4+Pi9Db250ZW50cyA1IDAgUj4+ZW5kb2JqCjQgMCBvYmo8PC9UeXBlL0ZvbnQvU3VidHlwZS9UeXBlMS9CYXNlRm9udC9UaW1lcy1Sb21hbj4+ZW5kb2JqCjUgMCBvYmo8PC9MZW5ndGggNDQ+PgpzdHJlYW0KQlQgL0YxIDEyIFRmIDEwMCA3MDAgVGQgKFRlc3QgZVNpZ24pIFRqIEVUCmVuZHN0cmVhbQplbmRvYmoKeHJlZgowIDYKMDAwMDAwMDAwMCA2NTUzNSBmCjAwMDAwMDAwMDkgMDAwMDAgbgowMDAwMDAwMDUyIDAwMDAwIG4KMDAwMDAwMDEwMSAwMDAwMCBuCjAwMDAwMDAyMTcgMDAwMDAgbgowMDAwMDAwMjg0IDAwMDAwIG4KdHJhaWxlcjw8L1NpemUgNi9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM3NgolJUVPRg=="


def post_json(url: str, payload: dict) -> tuple[int, dict, str]:
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        body = resp.read().decode("utf-8")
        return resp.status, json.loads(body), body


status_prepare, prepare_json, _ = post_json(
    "http://127.0.0.1:8080/prepare-pdf",
    {"pdfBase64": PDF_B64},
)

hash_b64 = prepare_json["hashBase64"]
prepared_b64 = prepare_json["preparedPdfBase64"]

status_sign, sign_json, sign_raw = post_json(
    "http://local-api.erp-portal.vn/esign/test-sign-hash",
    {"mst": "0312059023-000", "base64hash": hash_b64},
)

signature_b64 = ""
if isinstance(sign_json.get("data"), dict):
    signature_b64 = sign_json["data"].get("obj") or sign_json["data"].get("object") or ""

if not signature_b64:
    raise RuntimeError("No data.obj/data.object in sign response: " + sign_raw)

status_embed, embed_json, _ = post_json(
    "http://127.0.0.1:8080/embed-signature",
    {"preparedPdfBase64": prepared_b64, "signatureBase64": signature_b64},
)

signed_pdf_bytes = base64.b64decode(embed_json["signedPdfBase64"])

with open("/Users/tech1/Downloads/signingservice/target/test-sign-response.json", "w", encoding="utf-8") as f:
    f.write(json.dumps(sign_json, ensure_ascii=False, indent=2))

with open("/Users/tech1/Downloads/signingservice/target/test-signed.pdf", "wb") as f:
    f.write(signed_pdf_bytes)

print("prepare status:", status_prepare)
print("prepare hashBase64:", hash_b64)
print("sign status:", status_sign)
print("signature length:", len(signature_b64))
print("embed status:", status_embed)
print("signed pdf bytes:", len(signed_pdf_bytes))
print("saved: /Users/tech1/Downloads/signingservice/target/test-signed.pdf")
print("saved: /Users/tech1/Downloads/signingservice/target/test-sign-response.json")

