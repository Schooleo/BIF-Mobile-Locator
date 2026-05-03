package com.bif.server.features.sync.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncPushResult {
    private String clientChangeId;
    private String status;
    private String reasonCode;
}
