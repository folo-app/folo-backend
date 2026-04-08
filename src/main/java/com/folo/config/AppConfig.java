package com.folo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        JwtProperties.class,
        EmailVerificationProperties.class,
        AppEmailProperties.class,
        AppOpsProperties.class,
        FieldEncryptionProperties.class,
        FileStorageProperties.class,
        KisStubProperties.class,
        KisOAuthProperties.class,
        MarketDataSyncProperties.class,
        OpendartProperties.class,
        SocialAuthProperties.class,
        GoogleSocialAuthProperties.class,
        KakaoSocialAuthProperties.class,
        NaverSocialAuthProperties.class,
        AppleSocialAuthProperties.class
})
public class AppConfig {
}
