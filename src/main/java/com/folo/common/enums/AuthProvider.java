package com.folo.common.enums;

import java.util.Locale;

public enum AuthProvider {
    EMAIL,
    APPLE,
    GOOGLE,
    KAKAO,
    NAVER;

    public static AuthProvider fromPath(String value) {
        try {
            return AuthProvider.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("지원하지 않는 로그인 제공자입니다: " + value);
        }
    }
}
