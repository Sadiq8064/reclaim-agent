package dev.reclaim.ingest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class HmacValidator {

    private final String webhookSecret;

    public HmacValidator(@Value("${reclaim.razorpay.webhook-secret:reclaim_secret_hmac256}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public boolean isValidSignature(String payload, String signature) {
        if (payload == null || signature == null || signature.isBlank()) {
            return false;
        }
        String calculated = calculateHmacSha256(payload, webhookSecret);
        return MessageDigest.isEqual(calculated.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }

    public static String calculateHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(2 * rawHmac.length);
            for (byte b : rawHmac) {
                String hexByte = Integer.toHexString(0xFF & b);
                if (hexByte.length() == 1) {
                    hex.append('0');
                }
                hex.append(hexByte);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
    }
}
