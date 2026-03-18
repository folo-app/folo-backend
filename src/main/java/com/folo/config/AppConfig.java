package com.folo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        JwtProperties.class,
        EmailVerificationProperties.class,
        FieldEncryptionProperties.class,
        KisStubProperties.class
})
public class AppConfig {
}
