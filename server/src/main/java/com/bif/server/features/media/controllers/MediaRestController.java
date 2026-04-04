package com.bif.server.features.media.controllers;

import java.util.Locale;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bif.server.features.media.dto.rest.UploadSignatureResponse;
import com.bif.server.features.media.services.CloudinarySignatureService;

@RestController
@RequestMapping("/api/v1/media")
public class MediaRestController {

    private final CloudinarySignatureService cloudinarySignatureService;

    public MediaRestController(CloudinarySignatureService cloudinarySignatureService) {
        this.cloudinarySignatureService = cloudinarySignatureService;
    }

    @GetMapping("/upload-signature")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UploadSignatureResponse> getUploadSignature(
            Authentication authentication,
            @RequestParam(defaultValue = "avatar") String type,
            @RequestParam(required = false) String referenceId) {
        String userId = currentUserId(authentication);
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        String normalizedType = type == null
                ? "avatar"
                : type.trim().toLowerCase(Locale.ROOT);
        String normalizedReferenceId = normalizeReferenceId(referenceId);

        String folder;
        if ("avatar".equals(normalizedType)) {
            folder = "Bring-In-Friends/users/" + userId + "/avatars";
        } else if ("trip_stop".equals(normalizedType)) {
            if (normalizedReferenceId == null || normalizedReferenceId.isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            folder = "Bring-In-Friends/users/" + userId + "/trips/" + normalizedReferenceId;
        } else {
            return ResponseEntity.badRequest().build();
        }

        String tags = "user_" + userId + "," + normalizedType;
        if (normalizedReferenceId != null && !normalizedReferenceId.isBlank()) {
            tags = tags + ",ref_" + normalizedReferenceId;
        }

        UploadSignatureResponse response = cloudinarySignatureService
                .generateSignature(folder, tags);
        return ResponseEntity.ok(response);
    }

    private String currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        return authentication.getPrincipal().toString();
    }

    private String normalizeReferenceId(String referenceId) {
        if (referenceId == null) {
            return null;
        }
        String trimmed = referenceId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
