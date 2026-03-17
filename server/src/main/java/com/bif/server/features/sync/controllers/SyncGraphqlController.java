package com.bif.server.features.sync.controllers;

import com.bif.server.features.sync.models.SyncRequest;
import com.bif.server.features.sync.models.SyncResponse;
import com.bif.server.features.sync.services.SyncService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class SyncGraphqlController {
    private final SyncService syncService;

    public SyncGraphqlController(SyncService syncService) {
        this.syncService = syncService;
    }

    @QueryMapping
    public SyncResponse syncPreview(@Argument Integer lastPulledVersion) {
        SyncRequest request = new SyncRequest();
        request.setLastPulledVersion(lastPulledVersion == null ? 0 : lastPulledVersion);
        return syncService.sync(request);
    }

    @MutationMapping
    public SyncResponse sync(@Argument SyncRequest input) {
        return syncService.sync(input);
    }
}
