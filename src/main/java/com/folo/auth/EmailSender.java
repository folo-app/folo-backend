package com.folo.auth;

public interface EmailSender {

    void sendVerificationCode(String email, String code);

    void sendPasswordResetCode(String email, String code);

    void sendAccountIdReminder(String email, String loginId);
}
