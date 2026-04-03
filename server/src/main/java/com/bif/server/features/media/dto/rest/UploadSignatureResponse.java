package com.bif.server.features.media.dto.rest;

public record UploadSignatureResponse(
        String signature,
        long timestamp,
        String apiKey,
        String cloudName,
        String folder,
        String tags
) {
}
