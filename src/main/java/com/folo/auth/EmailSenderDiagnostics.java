package com.folo.auth;

import com.folo.config.AppEmailProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmailSenderDiagnostics {

    private final EmailSender emailSender;
    private final AppEmailProperties appEmailProperties;
    private final ObjectProvider<JavaMailSender> javaMailSenderProvider;
    private final String springMailHost;
    private final int springMailPort;
    private final String springMailUsername;

    public EmailSenderDiagnostics(
            EmailSender emailSender,
            AppEmailProperties appEmailProperties,
            ObjectProvider<JavaMailSender> javaMailSenderProvider,
            @Value("${spring.mail.host:}") String springMailHost,
            @Value("${spring.mail.port:0}") int springMailPort,
            @Value("${spring.mail.username:}") String springMailUsername
    ) {
        this.emailSender = emailSender;
        this.appEmailProperties = appEmailProperties;
        this.javaMailSenderProvider = javaMailSenderProvider;
        this.springMailHost = springMailHost;
        this.springMailPort = springMailPort;
        this.springMailUsername = springMailUsername;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logActiveSender() {
        log.info(
                "Email diagnostics: senderClass={}, smtpEnabled={}, javaMailSenderConfigured={}, host={}, port={}, username={}, fromAddress={}",
                emailSender.getClass().getSimpleName(),
                appEmailProperties.smtpEnabled(),
                javaMailSenderProvider.getIfAvailable() != null,
                blankToPlaceholder(springMailHost),
                springMailPort,
                blankToPlaceholder(springMailUsername),
                blankToPlaceholder(appEmailProperties.fromAddress())
        );
    }

    private String blankToPlaceholder(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        return value;
    }
}
