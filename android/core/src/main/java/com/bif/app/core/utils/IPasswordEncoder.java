package com.bif.app.core.utils;

public interface IPasswordEncoder {
    String encode(String rawPassword);
    boolean verify(String rawPassword, String encodedPassword);
}
