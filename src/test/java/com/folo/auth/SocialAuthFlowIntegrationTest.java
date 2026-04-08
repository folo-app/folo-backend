package com.folo.auth;

import com.folo.common.enums.AuthProvider;
import com.folo.notification.NotificationSettingRepository;
import com.folo.portfolio.PortfolioRepository;
import com.folo.user.User;
import com.folo.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestEmailSender.class)
class SocialAuthFlowIntegrationTest {

    @Autowired
    private SocialAuthService socialAuthService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthIdentityRepository userAuthIdentityRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void socialProfileCompletionCreatesIdentityAndDefaultResources() {
        String redirectUrl = socialAuthService.finalizeLogin(
                new SocialProviderIdentity(
                        AuthProvider.KAKAO,
                        "kakao-user-1",
                        "fresh-social@example.com",
                        true,
                        "FreshSocial",
                        "https://example.com/profile.png"
                ),
                "device-a",
                "iPhone"
        );

        String handoffCode = UriComponentsBuilder.fromUriString(redirectUrl)
                .build()
                .getQueryParams()
                .getFirst("handoffCode");
        assertNotNull(handoffCode);

        SocialAuthExchangeResponse exchangeResponse = socialAuthService.exchange(
                new SocialAuthExchangeRequest(handoffCode, null, null)
        );
        assertEquals(SocialAuthExchangeStatus.PROFILE_SETUP_REQUIRED, exchangeResponse.status());
        assertNotNull(exchangeResponse.pendingToken());

        AuthResponse session = socialAuthService.completeProfile(
                new SocialAuthCompleteProfileRequest(
                        exchangeResponse.pendingToken(),
                        "FreshSocial",
                        "https://example.com/profile.png"
                )
        );

        assertEquals(AuthProvider.KAKAO, session.authProvider());
        assertEquals("fresh-social@example.com", session.email());

        UserAuthIdentity identity = userAuthIdentityRepository.findByProviderAndProviderUserId(
                AuthProvider.KAKAO,
                "kakao-user-1"
        ).orElseThrow();
        User savedUser = userRepository.findById(identity.getUser().getId()).orElseThrow();
        assertEquals("FreshSocial", savedUser.getNickname());
        assertTrue(portfolioRepository.findByUserId(savedUser.getId()).isPresent());
        assertTrue(notificationSettingRepository.findByUserId(savedUser.getId()).isPresent());
    }

    @Test
    void refreshWorksForSocialIdentityWithoutEmail() throws Exception {
        User user = userRepository.save(new User("google-refresh", "GoogleRefresh", null));
        UserAuthIdentity identity = userAuthIdentityRepository.save(new UserAuthIdentity(
                user,
                AuthProvider.GOOGLE,
                "google-user-1",
                null,
                false
        ));

        String redirectUrl = socialAuthService.finalizeLogin(
                new SocialProviderIdentity(
                        AuthProvider.GOOGLE,
                        "google-user-1",
                        null,
                        false,
                        "GoogleRefresh",
                        null
                ),
                "device-b",
                "Pixel"
        );
        String handoffCode = UriComponentsBuilder.fromUriString(redirectUrl)
                .build()
                .getQueryParams()
                .getFirst("handoffCode");
        assertNotNull(handoffCode);

        SocialAuthExchangeResponse exchangeResponse = socialAuthService.exchange(
                new SocialAuthExchangeRequest(handoffCode, null, null)
        );

        assertEquals(SocialAuthExchangeStatus.AUTHENTICATED, exchangeResponse.status());
        assertNotNull(exchangeResponse.session());
        assertEquals(AuthProvider.GOOGLE, exchangeResponse.session().authProvider());
        assertEquals(identity.getUser().getId(), exchangeResponse.session().userId());

        String refreshBody = """
                {
                  "refreshToken": "%s"
                }
                """.formatted(exchangeResponse.session().refreshToken());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authProvider").value("GOOGLE"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }
}
