package com.bif.app.core.network.dto.profile;

public class UpdateMyProfileRequest {
    public String name;
    public String avatarLetter;
    public Integer avatarColor;

    public UpdateMyProfileRequest(String name, String avatarLetter, Integer avatarColor) {
        this.name = name;
        this.avatarLetter = avatarLetter;
        this.avatarColor = avatarColor;
    }
}
