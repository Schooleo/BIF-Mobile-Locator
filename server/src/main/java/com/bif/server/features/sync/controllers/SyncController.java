package com.bif.server.features.sync.controllers;

import com.bif.server.features.sync.models.SyncRequest;
import com.bif.server.features.sync.models.SyncResponse;
import com.bif.server.features.sync.services.SyncService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/sync")
public class SyncController {
    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping
    public SyncResponse sync(@RequestBody SyncRequest request,
                             Authentication authentication) {
        String userId = currentUserId(authentication);
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Authentication required");
        }

        request.setUserId(userId);
        return syncService.sync(request);
    }

    private String currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        return authentication.getPrincipal().toString();
    }
}
