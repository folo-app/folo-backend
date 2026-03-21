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
        sendMail(
                email,
                "[FOLO] 이메일 인증 코드",
                """
                        FOLO 이메일 인증 코드입니다.

                        인증 코드: %s

                        앱으로 돌아가 위 코드를 입력해 인증을 완료해 주세요.
                        """.formatted(code),
                "verification"
        );
    }

    @Override
    public void sendPasswordResetCode(String email, String code) {
        sendMail(
                email,
                "[FOLO] 비밀번호 재설정 코드",
                """
                        FOLO 비밀번호 재설정 코드입니다.

                        재설정 코드: %s

                        앱으로 돌아가 새 비밀번호와 함께 위 코드를 입력해 주세요.
                        본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.
                        """.formatted(code),
                "password-reset"
        );
    }

    @Override
    public void sendAccountIdReminder(String email, String loginId) {
        sendMail(
                email,
                "[FOLO] 로그인 아이디 안내",
                """
                        요청하신 FOLO 로그인 아이디 안내입니다.

                        로그인 아이디(이메일): %s

                        앱에서 위 이메일 주소로 로그인해 주세요.
                        본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.
                        """.formatted(loginId),
                "account-reminder"
        );
    }

    private void sendMail(String email, String subject, String body, String purpose) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(formatFrom());
        message.setTo(email);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            log.error(
                    "SMTP {} email send failed. to={}, from={}, reason={}",
                    purpose,
                    email,
                    appEmailProperties.fromAddress(),
                    exception.getMessage(),
                    exception
            );
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
