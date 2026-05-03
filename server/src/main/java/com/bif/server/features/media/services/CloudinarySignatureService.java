package com.bif.server.features.media.services;

import com.bif.server.features.media.dto.rest.UploadSignatureResponse;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CloudinarySignatureService {

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final boolean failFast;
    private final Cloudinary cloudinary;

    public CloudinarySignatureService(
            @Value("${cloudinary.url:}") String cloudinaryUrl,
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret,
            @Value("${cloudinary.fail-fast:false}") boolean failFast) {
        String normalizedUrl = cloudinaryUrl != null ? cloudinaryUrl.trim() : "";
        String normalizedCloudName = cloudName != null ? cloudName.trim() : "";
        String normalizedApiKey = apiKey != null ? apiKey.trim() : "";
        String normalizedApiSecret = apiSecret != null ? apiSecret.trim() : "";

        if (!normalizedUrl.isEmpty()) {
            this.cloudinary = new Cloudinary(normalizedUrl);
        } else {
            this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", normalizedCloudName,
                "api_key", normalizedApiKey,
                "api_secret", normalizedApiSecret));
        }

        this.cloudName = firstNonBlank(normalizedCloudName,
            this.cloudinary.config.cloudName);
        this.apiKey = firstNonBlank(normalizedApiKey,
            this.cloudinary.config.apiKey);
        this.apiSecret = firstNonBlank(normalizedApiSecret,
            this.cloudinary.config.apiSecret);
        this.failFast = failFast;
    }

    @PostConstruct
    void validateConfigurationOnStartup() {
        if (failFast) {
            ensureConfigured();
        }
    }

    public UploadSignatureResponse generateSignature(String folder, String tags) {
        ensureConfigured();
        if (folder == null || folder.isBlank()) {
            throw new IllegalArgumentException("folder must not be blank");
        }

        long timestamp = Instant.now().getEpochSecond();
        String normalizedFolder = folder.trim();
        String normalizedTags = tags != null && !tags.isBlank()
                ? tags.trim()
                : null;
        String publicId = UUID.randomUUID().toString();

        Map<String, Object> paramsToSign = new HashMap<>();
        paramsToSign.put("timestamp", timestamp);
        paramsToSign.put("folder", normalizedFolder);
        paramsToSign.put("public_id", publicId);
        if (normalizedTags != null) {
            paramsToSign.put("tags", normalizedTags);
        }

        String signature = cloudinary.apiSignRequest(paramsToSign, apiSecret);
        return new UploadSignatureResponse(
                signature,
                timestamp,
                apiKey,
                cloudName,
                normalizedFolder,
                normalizedTags,
                publicId
        );
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback != null ? fallback.trim() : "";
    }

    private void ensureConfigured() {
        if (cloudName.isEmpty() || apiKey.isEmpty() || apiSecret.isEmpty()) {
            throw new IllegalStateException("Cloudinary is not configured");
        }
    }
}
