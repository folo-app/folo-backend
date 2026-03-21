package com.folo.auth;

public interface EmailSender {

    void sendVerificationCode(String email, String code);

    void sendTemporaryPassword(String email, String temporaryPassword);
}
