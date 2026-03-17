package com.bif.server.features.sync.services;

import com.bif.server.features.sync.models.SyncRequest;
import com.bif.server.features.sync.models.SyncResponse;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class SyncService {

    public SyncResponse sync(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        long baseline = Math.max(0, request.getLastPulledVersion());
        long pushed = request.getPushedChanges() == null ? 0 : request.getPushedChanges().size();

        response.setCurrentServerVersion(baseline + pushed);
        response.setPulledChanges(Collections.emptyList());
        return response;
    }
}
