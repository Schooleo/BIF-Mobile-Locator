package com.bif.app.core.utils;

import at.favre.lib.crypto.bcrypt.BCrypt;

public class BcryptPasswordEncoder implements IPasswordEncoder {
    private static final int BCRYPT_COST = 12;

    @Override
    public String encode(String rawPassword){
        return BCrypt.withDefaults()
                .hashToString(BCRYPT_COST, rawPassword.toCharArray());
    }

    @Override
    public boolean verify(String rawPassword, String encodedPassword) {
        BCrypt.Result result = BCrypt.verifyer()
                .verify(rawPassword.toCharArray(), encodedPassword.toCharArray());
        return result.verified;
    }
}
