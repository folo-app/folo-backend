package com.folo.auth;

import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.config.AppEmailProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Primary
@ConditionalOnProperty(prefix = "app.email", name = "smtp-enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final AppEmailProperties appEmailProperties;

    public SmtpEmailSender(JavaMailSender mailSender, AppEmailProperties appEmailProperties) {
        this.mailSender = mailSender;
        this.appEmailProperties = appEmailProperties;
    }

    @Override
    public void sendVerificationCode(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(formatFrom());
        message.setTo(email);
        message.setSubject("[FOLO] 이메일 인증 코드");
        message.setText("""
                FOLO 이메일 인증 코드입니다.

                인증 코드: %s

                앱으로 돌아가 위 코드를 입력해 인증을 완료해 주세요.
                """.formatted(code));

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            log.error("SMTP verification email send failed. to={}, from={}, reason={}", email, appEmailProperties.fromAddress(), exception.getMessage(), exception);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "이메일 발송에 실패했습니다.");
        }
    }

    @Override
    public void sendTemporaryPassword(String email, String temporaryPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(formatFrom());
        message.setTo(email);
        message.setSubject("[FOLO] 임시 비밀번호 안내");
        message.setText("""
                FOLO 임시 비밀번호 안내입니다.

                임시 비밀번호: %s

                로그인 후 바로 비밀번호를 변경해 주세요.
                기존 로그인 세션은 모두 종료되었습니다.
                """.formatted(temporaryPassword));

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            log.error("SMTP temporary password email send failed. to={}, from={}, reason={}", email, appEmailProperties.fromAddress(), exception.getMessage(), exception);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "이메일 발송에 실패했습니다.");
        }
    }

    private String formatFrom() {
        String fromName = appEmailProperties.fromName();
        String fromAddress = appEmailProperties.fromAddress();
        if (fromName == null || fromName.isBlank()) {
            return fromAddress;
        }
        return "%s <%s>".formatted(fromName, fromAddress);
    }
}
