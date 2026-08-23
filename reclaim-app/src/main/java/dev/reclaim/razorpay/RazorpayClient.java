package dev.reclaim.razorpay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class RazorpayClient {

    private static final Logger log = LoggerFactory.getLogger(RazorpayClient.class);

    private final String keyId;
    private final String keySecret;
    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public RazorpayClient(
            @Value("${reclaim.razorpay.key-id:rzp_test_placeholder}") String keyId,
            @Value("${reclaim.razorpay.key-secret:secret_placeholder}") String keySecret,
            @Value("${reclaim.razorpay.base-url:https://api.razorpay.com/v1}") String baseUrl,
            ObjectMapper objectMapper) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    private HttpHeaders createAuthHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        String auth = keyId + ":" + keySecret;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedAuth);
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("X-Razorpay-Idempotency-Key", idempotencyKey);
        }
        return headers;
    }

    @Retry(name = "razorpayClient")
    @CircuitBreaker(name = "razorpayClient")
    public RazorpayPaymentLinkResponse createPaymentLink(
            long amountPaise,
            String customerName,
            String customerContact,
            String customerEmail,
            String description,
            String idempotencyKey) {

        if (keyId.startsWith("rzp_test_placeholder") || keyId.equals("rzp_test_dummy")) {
            // Local simulated realistic mock response
            String linkId = "plink_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            String shortUrl = "https://rzp.io/i/" + linkId.substring(6);
            log.info("Simulated real Razorpay payment link created: id={}, url={}", linkId, shortUrl);
            return new RazorpayPaymentLinkResponse(linkId, shortUrl, "created", amountPaise);
        }

        try {
            String url = baseUrl + "/payment_links";
            Map<String, Object> body = Map.of(
                    "amount", amountPaise,
                    "currency", "INR",
                    "accept_partial", false,
                    "description", description,
                    "customer", Map.of(
                            "name", customerName != null ? customerName : "Valued Customer",
                            "contact", customerContact != null ? customerContact : "+919876543210",
                            "email", customerEmail != null ? customerEmail : "customer@example.com"
                    ),
                    "notify", Map.of("sms", true, "email", true)
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, createAuthHeaders(idempotencyKey));
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String id = root.path("id").asText();
                String shortUrl = root.path("short_url").asText();
                String status = root.path("status").asText();
                return new RazorpayPaymentLinkResponse(id, shortUrl, status, amountPaise);
            }
        } catch (Exception e) {
            log.error("Error creating Razorpay payment link: {}", e.getMessage());
            throw new RuntimeException("Razorpay API failure", e);
        }
        throw new RuntimeException("Unexpected response from Razorpay payment link API");
    }

    @Retry(name = "razorpayClient")
    @CircuitBreaker(name = "razorpayClient")
    public RazorpayChargeRetryResponse retrySubscriptionCharge(String subscriptionId, String idempotencyKey) {
        if (keyId.startsWith("rzp_test_placeholder") || keyId.equals("rzp_test_dummy")) {
            String invoiceId = "inv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            log.info("Simulated real Razorpay subscription charge retry scheduled for sub {}: inv={}", subscriptionId, invoiceId);
            return new RazorpayChargeRetryResponse(invoiceId, "issued", true);
        }

        try {
            String url = baseUrl + "/subscriptions/" + subscriptionId + "/charge";
            HttpEntity<String> entity = new HttpEntity<>("{}", createAuthHeaders(idempotencyKey));
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String invoiceId = root.path("id").asText();
                String status = root.path("status").asText();
                return new RazorpayChargeRetryResponse(invoiceId, status, true);
            }
        } catch (Exception e) {
            log.error("Error retrying subscription charge on Razorpay: {}", e.getMessage());
            throw new RuntimeException("Razorpay Subscription charge failure", e);
        }
        throw new RuntimeException("Unexpected response from Razorpay charge API");
    }

    public boolean reconcileSubscriptionStatus(String subscriptionId) {
        if (keyId.startsWith("rzp_test_placeholder") || keyId.equals("rzp_test_dummy")) {
            return true; // healthy
        }
        try {
            String url = baseUrl + "/subscriptions/" + subscriptionId;
            HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders(null));
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String status = root.path("status").asText();
                return !"cancelled".equalsIgnoreCase(status) && !"completed".equalsIgnoreCase(status);
            }
        } catch (Exception e) {
            log.warn("Subscription reconciliation check encountered warning: {}", e.getMessage());
        }
        return false;
    }

    public record RazorpayPaymentLinkResponse(String id, String shortUrl, String status, long amountPaise) {}
    public record RazorpayChargeRetryResponse(String invoiceId, String status, boolean success) {}
}
