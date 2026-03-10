package com.bif.app.core.utils;

import android.util.Base64;

public class Base64PasswordEncoder implements IPasswordEncoder {
    @Override
    public String encode(String rawPassword){
        return Base64.encodeToString(
                rawPassword.getBytes(),
                Base64.NO_WRAP
        );
    }

    @Override
    public boolean verify(String rawPassword, String encodedPassword) {
        String encodedRawPassword = encode(rawPassword);
        return encodedRawPassword.equals(encodedPassword);
    }
}
