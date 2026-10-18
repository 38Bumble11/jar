package com.aurasmp.auralink;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Sends signed requests to the Discord bot's API.
 *
 * Every request carries two headers:
 *   X-Timestamp - current time in seconds
 *   X-Signature - HMAC-SHA256 of "timestamp.body" using the shared secret
 * so the bot knows the request really came from this server.
 * (The secret itself is never sent.)
 */
public final class ApiClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private ApiClient() {}

    /** POSTs a JSON body to baseUrl + path and completes with the response body. */
    public static CompletableFuture<String> post(String baseUrl, String secret, String path, String json) {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = sign(secret, timestamp + "." + json);

        HttpRequest request = HttpRequest.newBuilder(URI.create(trimTrailingSlash(baseUrl) + path))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("X-Timestamp", timestamp)
                .header("X-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(HttpResponse::body);
    }

    static String sign(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
