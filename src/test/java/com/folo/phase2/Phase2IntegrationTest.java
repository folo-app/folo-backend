package com.folo.phase2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.folo.auth.InMemoryEmailVerificationStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase2IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryEmailVerificationStore verificationStore;

    @Test
    void reminderCrudAndCsvImportFlowWork() throws Exception {
        String token = createVerifiedUser("phase2@example.com", "phase2User");

        String reminderBody = """
                {
                  "ticker": "AAPL",
                  "market": "NASDAQ",
                  "amount": 300000,
                  "dayOfMonth": 5
                }
                """;

        mockMvc.perform(post("/reminders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reminderBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ticker").value("AAPL"))
                .andExpect(jsonPath("$.data.dayOfMonth").value(5));

        mockMvc.perform(get("/reminders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reminders[0].ticker").value("AAPL"));

        String csv = """
                ticker,market,tradeType,quantity,price,tradedAt,comment,visibility
                AAPL,NASDAQ,BUY,2,180,2025-03-12T10:15:00,imported trade,PRIVATE
                """;

        MockMultipartFile file = new MockMultipartFile("file", "trades.csv", "text/csv", csv.getBytes());
        String importResponse = mockMvc.perform(multipart("/portfolio/import/csv")
                        .file(file)
                        .param("broker", "KIS")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedTrades").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode importJson = objectMapper.readTree(importResponse);
        long importResultId = importJson.get("data").get("preview").get(0).get("importResultId").asLong();

        String confirmBody = """
                {
                  "importResultIds": [%d]
                }
                """.formatted(importResultId);

        mockMvc.perform(post("/portfolio/import/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.savedTrades").value(1))
                .andExpect(jsonPath("$.data.confirmedImportResultIds[0]").value(importResultId))
                .andExpect(jsonPath("$.data.tradeIds.length()").value(1));

        mockMvc.perform(post("/portfolio/import/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE"));

        mockMvc.perform(get("/portfolio")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.holdings[0].ticker").value("AAPL"));
    }

    @Test
    void kisKeyPatchEndpointStoresKeys() throws Exception {
        String token = createVerifiedUser("kis@example.com", "kisUser");

        String body = """
                {
                  "kisAppKey": "PS12345678",
                  "kisAppSecret": "secret-value-1234"
                }
                """;

        mockMvc.perform(patch("/users/me/kis-key")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
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

        return objectMapper.readTree(response).get("data").get("accessToken").asText();
    }
}
