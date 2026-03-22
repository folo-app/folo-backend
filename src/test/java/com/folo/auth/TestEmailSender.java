package com.folo.auth;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Primary
@TestComponent
public class TestEmailSender implements EmailSender {

    private final Map<String, String> verificationCodes = new ConcurrentHashMap<>();
    private final Map<String, String> temporaryPasswords = new ConcurrentHashMap<>();

    @Override
    public void sendVerificationCode(String email, String code) {
        verificationCodes.put(email, code);
    }

    @Override
    public void sendTemporaryPassword(String email, String temporaryPassword) {
        temporaryPasswords.put(email, temporaryPassword);
    }

    public String getVerificationCode(String email) {
        return verificationCodes.get(email);
    }

    public String getTemporaryPassword(String email) {
        return temporaryPasswords.get(email);
    }
}
