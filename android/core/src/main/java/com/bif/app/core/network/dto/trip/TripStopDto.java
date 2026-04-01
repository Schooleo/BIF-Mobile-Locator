package com.bif.app.core.network.dto.trip;

import com.bif.app.core.network.dto.chat.ChatMessageDto;

public class TripStopDto {
    public String id;
    public String tripId;
    public String title;
    public String note;
    public ChatMessageDto.LocationDto location;
    public String arrivalTime;
    public String departureTime;
    public int orderIndex;
    public long serverVersion;
    public boolean deleted;
}

