package com.folo.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingEmailSender implements EmailSender {

    @Override
    public void sendVerificationCode(String email, String code) {
        log.info("FOLO verification code for {} is {}", email, code);
    }

    @Override
    public void sendPasswordResetCode(String email, String code) {
        log.info("FOLO password reset code for {} is {}", email, code);
    }

    @Override
    public void sendAccountIdReminder(String email, String loginId) {
        log.info("FOLO account id reminder for {} is {}", email, loginId);
    }
}
