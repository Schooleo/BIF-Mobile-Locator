package com.bif.app.core.network.dto;

import com.google.gson.annotations.SerializedName;

public class UserApiModel {
    public String id;
    
    @SerializedName(value = "name", alternate = {"username", "displayName"})
    public String name;
    
    public String email;
    public String avatarLetter;
    public int avatarColor;
    public boolean isOnline;
}
