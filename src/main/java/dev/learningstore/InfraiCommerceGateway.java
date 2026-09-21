package dev.learningstore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InfraiCommerceGateway implements CommerceGateway {
    private final InfraiConfig config;
    private final HttpClient http;

    public InfraiCommerceGateway(InfraiConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    InfraiCommerceGateway(InfraiConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public void configureAutoRecharge(double triggerBalance, double rechargeAmount, String idempotencyKey) {
        // Canonical capability: infrai.account.autorecharge.configure
        request("PUT", "/v1/account/autorecharge/configure", Map.of(
                "trigger_balance", triggerBalance,
                "recharge_amount", rechargeAmount), idempotencyKey);
    }

    @Override
    public double accountBalance() {
        Map<String, Object> data = request("GET", "/v1/account/balance", null, null);
        Object balance = data.get("balance");
        if (!(balance instanceof Number number)) {
            throw new IllegalStateException("Balance response did not contain a numeric balance");
        }
        return number.doubleValue();
    }

    @Override
    public String sendEmail(String to, String subject, String text, String idempotencyKey) {
        // Canonical capability: infrai.email.send
        Map<String, Object> data = request("POST", "/v1/email/send", Map.of(
                "to", to,
                "subject", subject,
                "body", text), idempotencyKey);
        Object messageId = data.get("message_id");
        if (!(messageId instanceof String id) || id.isBlank()) {
            throw new IllegalStateException("Email response did not contain message_id");
        }
        return id;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> request(String method, String path, Map<String, ?> body, String idempotencyKey) {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(config.baseUrl().resolve(path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json");
            if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
            if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
            else builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(Json.object(body)));

            HttpResponse<String> response;
            try {
                response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException exception) {
                throw new IllegalStateException("Could not reach Infrai", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Request interrupted", exception);
            }

            Object decoded = Json.parse(response.body());
            if (!(decoded instanceof Map<?, ?> rawEnvelope)) {
                throw new IllegalStateException("Infrai response was not an envelope");
            }
            Map<String, Object> envelope = (Map<String, Object>) rawEnvelope;
            if (response.statusCode() == 429 && attempt < 3) {
                pause(retryDelayMillis(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Object rawError = envelope.get("error");
                Map<String, Object> error = rawError instanceof Map<?, ?> map
                        ? (Map<String, Object>) map : Map.of("message", "Request rejected");
                String code = String.valueOf(error.getOrDefault("code", "INFRAI_REQUEST_REJECTED"));
                throw new InfraiException(code, error, response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new IllegalStateException("Infrai transport response: " + response.statusCode());
            }
            Object rawData = envelope.get("data");
            return rawData instanceof Map<?, ?> map
                    ? new LinkedHashMap<>((Map<String, Object>) map) : Map.of();
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(InfraiCommerceGateway::parseRetryAfter)
                .orElse(250L * (1L << attempt));
    }

    private static long parseRetryAfter(String seconds) {
        try { return Math.max(0L, Long.parseLong(seconds.trim()) * 1_000L); }
        catch (NumberFormatException ignored) { return 1_000L; }
    }

    private static void pause(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", exception);
        }
    }
}
