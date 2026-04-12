package com.bif.app.core.network.dto.trip;

import com.bif.app.core.network.dto.chat.ChatMessageDto;

public class TripStopDto {
    public String id;
    public String tripId;
    public String title;
    public String address;
    public String note;
    public String photoUrl;
    public String addedByUserId;
    public String addedByName;
    public String addedByAvatarLetter;
    public Integer addedByAvatarColor;
    public ChatMessageDto.LocationDto location;
    public Double latitude;
    public Double longitude;
    public String arrivalTime;
    public String departureTime;
    public int orderIndex;
    public long serverVersion;
    public boolean deleted;
}
