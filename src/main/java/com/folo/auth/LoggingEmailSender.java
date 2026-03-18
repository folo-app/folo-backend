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
}
