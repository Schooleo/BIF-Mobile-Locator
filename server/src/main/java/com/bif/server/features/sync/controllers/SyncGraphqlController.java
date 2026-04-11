package com.bif.server.features.sync.controllers;

import com.bif.server.features.sync.models.SyncRequest;
import com.bif.server.features.sync.models.SyncResponse;
import com.bif.server.features.sync.services.SyncService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class SyncGraphqlController {
    private final SyncService syncService;

    public SyncGraphqlController(SyncService syncService) {
        this.syncService = syncService;
    }

    @QueryMapping
    public SyncResponse syncPreview(@Argument Integer lastPulledVersion,
                                    Authentication authentication) {
        SyncRequest request = new SyncRequest();
        String userId = currentUserId(authentication);
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Authentication required");
        }

        request.setUserId(userId);
        request.setLastPulledVersion(lastPulledVersion == null ? 0 : lastPulledVersion);
        return syncService.sync(request);
    }

    @MutationMapping
    public SyncResponse sync(@Argument SyncRequest input,
                             Authentication authentication) {
        String userId = currentUserId(authentication);
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Authentication required");
        }

        input.setUserId(userId);
        return syncService.sync(input);
    }

    private String currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        return authentication.getName();
    }
}
