package com.bif.app.core.network.dto;

import java.util.List;

public class SyncResponseDto {
    public long currentServerVersion;
    public List<SyncChangeDto> pulledChanges;
    public List<SyncConflictDto> conflicts;
}
