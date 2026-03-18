package com.folo.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TradePortfolioIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryEmailVerificationStore verificationStore;

    @Test
    void creatingTradeRecalculatesPortfolio() throws Exception {
        String token = createVerifiedUser("trade-user@example.com", "tradeUser");

        String tradeBody = """
                {
                  "ticker": "005930",
                  "market": "KRX",
                  "tradeType": "BUY",
                  "quantity": 10,
                  "price": 70000,
                  "comment": "초기 매수",
                  "visibility": "PUBLIC"
                }
                """;

        mockMvc.perform(post("/trades")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tradeId").isNumber())
                .andExpect(jsonPath("$.data.totalAmount").value(700000.0));

        mockMvc.perform(get("/portfolio")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalInvested").value(700000.0))
                .andExpect(jsonPath("$.data.holdings[0].ticker").value("005930"))
                .andExpect(jsonPath("$.data.holdings[0].quantity").value(10.0));
    }

    private String createVerifiedUser(String email, String nickname) throws Exception {
        String signupBody = """
                {
                  "email": "%s",
                  "password": "Password123!",
                  "nickname": "%s"
                }
                """.formatted(email, nickname);

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isCreated());

        String confirmBody = """
                {
                  "email": "%s",
                  "code": "%s"
                }
                """.formatted(email, verificationStore.getCode(email));

        String response = mockMvc.perform(post("/auth/email/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        return jsonNode.get("data").get("accessToken").asText();
    }
}
