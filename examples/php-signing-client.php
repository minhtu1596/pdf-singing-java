<?php

declare(strict_types=1);

/**
 * Example PHP client for Java signing service (2-step external signing).
 *
 * Flow:
 * 1) Send pdfBase64 to /prepare-pdf -> receive data.preparedPdfBase64 + data.hashBase64
 * 2) Send hashBase64 to HSM/CyberSign -> receive CMS/PKCS#7 signature bytes
 * 3) Send preparedPdfBase64 + signatureBase64 to /embed-signature -> receive data.signedPdfBase64
 */

$javaBaseUrl = 'http://127.0.0.1:8080';
$inputPdfPath = __DIR__ . '/input.pdf';
$outputPdfPath = __DIR__ . '/signed-output.pdf';

if (!file_exists($inputPdfPath)) {
    throw new RuntimeException("Input PDF not found: {$inputPdfPath}");
}

$pdfBase64 = base64_encode((string) file_get_contents($inputPdfPath));

$prepareResponse = postJson($javaBaseUrl . '/prepare-pdf', [
    'pdfBase64' => $pdfBase64,
]);

if (empty($prepareResponse['preparedPdfBase64']) || empty($prepareResponse['hashBase64'])) {
    throw new RuntimeException('Invalid /prepare-pdf response');
}

$preparedPdfBase64 = $prepareResponse['preparedPdfBase64'];
$hashBase64 = $prepareResponse['hashBase64'];

// Replace this stub with your real HSM signing call.
$signatureBase64 = signHashWithHsm($hashBase64);

$embedResponse = postJson($javaBaseUrl . '/embed-signature', [
    'preparedPdfBase64' => $preparedPdfBase64,
    'signatureBase64' => $signatureBase64,
]);

if (empty($embedResponse['signedPdfBase64'])) {
    throw new RuntimeException('Invalid /embed-signature response');
}

file_put_contents($outputPdfPath, base64_decode($embedResponse['signedPdfBase64'], true));

echo "Done. Signed PDF saved: {$outputPdfPath}" . PHP_EOL;

/**
 * Call remote HSM signing API.
 *
 * IMPORTANT:
 * - Input is base64(SHA-256 hash bytes)
 * - Output must be base64(raw CMS/PKCS#7 DER bytes), NOT hex string
 */
function signHashWithHsm(string $hashBase64): string
{
    // TODO: Replace by your real API endpoint and payload fields.
    // Example pattern:
    // $hsmResponse = postJson('https://hsm.example/sign', ['hashBase64' => $hashBase64]);
    // return $hsmResponse['signatureBase64'];

    throw new RuntimeException('Please implement signHashWithHsm() for your Cyber/HSM provider.');
}

/**
 * @return array<string, mixed>
 */
function postJson(string $url, array $payload): array
{
    $ch = curl_init($url);
    if ($ch === false) {
        throw new RuntimeException('curl_init failed');
    }

    $jsonBody = json_encode($payload, JSON_UNESCAPED_SLASHES);
    if ($jsonBody === false) {
        throw new RuntimeException('json_encode failed');
    }

    curl_setopt_array($ch, [
        CURLOPT_POST => true,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER => [
            'Content-Type: application/json',
            'Accept: application/json',
        ],
        CURLOPT_POSTFIELDS => $jsonBody,
        CURLOPT_TIMEOUT => 60,
    ]);

    $raw = curl_exec($ch);
    if ($raw === false) {
        $err = curl_error($ch);
        curl_close($ch);
        throw new RuntimeException('cURL error: ' . $err);
    }

    $status = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);

    $decoded = json_decode($raw, true);
    if (!is_array($decoded)) {
        throw new RuntimeException("Invalid JSON response from {$url}: {$raw}");
    }

    if ($status < 200 || $status >= 300) {
        throw new RuntimeException("HTTP {$status} from {$url}: {$raw}");
    }

    if (!isset($decoded['success']) || !array_key_exists('data', $decoded)) {
        throw new RuntimeException("Invalid API envelope from {$url}: {$raw}");
    }

    if ($decoded['success'] !== true) {
        $code = (string)($decoded['code'] ?? 'UNKNOWN_ERROR');
        $message = (string)($decoded['message'] ?? 'Request failed');
        throw new RuntimeException("API {$code}: {$message}");
    }

    if (!is_array($decoded['data'])) {
        throw new RuntimeException("Missing/invalid data payload from {$url}: {$raw}");
    }

    return $decoded['data'];
}

