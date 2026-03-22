package com.folo.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestEmailSender.class)
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryEmailVerificationStore verificationStore;

    @Autowired
    private TestEmailSender testEmailSender;

    @Autowired
    private UserAuthIdentityRepository userAuthIdentityRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void signupConfirmLoginAndWithdrawFlowWorks() throws Exception {
        String signupBody = """
                {
                  "email": "alpha@example.com",
                  "password": "Password123!",
                  "nickname": "alphaInvestor",
                  "profileImage": "https://example.com/a.png"
                }
                """;

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verificationRequired").value(true));

        String code = verificationStore.getCode("alpha@example.com");

        String confirmBody = """
                {
                  "email": "alpha@example.com",
                  "code": "%s"
                }
                """.formatted(code);

        String confirmResponse = mockMvc.perform(post("/auth/email/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode confirmed = objectMapper.readTree(confirmResponse);
        String accessToken = confirmed.get("data").get("accessToken").asText();
        String refreshToken = confirmed.get("data").get("refreshToken").asText();

        String loginBody = """
                {
                  "email": "alpha@example.com",
                  "password": "Password123!"
                }
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("alpha@example.com"));

        String logoutBody = """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken);

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logoutBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(delete("/auth/withdraw")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void findIdReturnsMaskedLoginIdForNickname() throws Exception {
        signupAndConfirm("masked@example.com", "Password123!", "maskFinder");

        String findIdBody = """
                {
                  "nickname": "maskFinder"
                }
                """;

        mockMvc.perform(post("/auth/find-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(findIdBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.found").value(true))
                .andExpect(jsonPath("$.data.maskedLoginId").value("ma****@example.com"));
    }

    @Test
    void resetPasswordIssuesTemporaryPasswordRevokesRefreshTokensAndAllowsNewLogin() throws Exception {
        signupAndConfirm("recover@example.com", "Password123!", "recoverUser");

        String loginBody = """
                {
                  "email": "recover@example.com",
                  "password": "Password123!"
                }
                """;

        String loginResponse = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode loggedIn = objectMapper.readTree(loginResponse);
        String refreshToken = loggedIn.get("data").get("refreshToken").asText();

        String resetBody = """
                {
                  "email": "recover@example.com"
                }
                """;

        mockMvc.perform(post("/auth/password/reset-temp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        String temporaryPassword = testEmailSender.getTemporaryPassword("recover@example.com");
        UserAuthIdentity identity = userAuthIdentityRepository.findByEmail("recover@example.com")
                .orElseThrow();

        org.junit.jupiter.api.Assertions.assertNotNull(temporaryPassword);
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches(temporaryPassword, identity.getPasswordHash()));
        org.junit.jupiter.api.Assertions.assertTrue(identity.isEmailVerified());
        org.junit.jupiter.api.Assertions.assertTrue(
                refreshTokenRepository.findByUserIdAndRevokedAtIsNull(identity.getUser().getId()).isEmpty()
        );

        String oldPasswordLoginBody = """
                {
                  "email": "recover@example.com",
                  "password": "Password123!"
                }
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oldPasswordLoginBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        String tempPasswordLoginBody = """
                {
                  "email": "recover@example.com",
                  "password": "%s"
                }
                """.formatted(temporaryPassword);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tempPasswordLoginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("recover@example.com"));

        String refreshRequestBody = """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    private void signupAndConfirm(String email, String password, String nickname) throws Exception {
        String signupBody = """
                {
                  "email": "%s",
                  "password": "%s",
                  "nickname": "%s",
                  "profileImage": "https://example.com/a.png"
                }
                """.formatted(email, password, nickname);

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isCreated());

        String code = verificationStore.getCode(email);
        String confirmBody = """
                {
                  "email": "%s",
                  "code": "%s"
                }
                """.formatted(email, code);

        mockMvc.perform(post("/auth/email/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk());
    }
}
