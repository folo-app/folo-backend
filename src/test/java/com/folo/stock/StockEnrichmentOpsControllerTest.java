package com.folo.stock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.ops.trigger-secret=test-ops-secret")
class StockEnrichmentOpsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StockDividendEnrichmentService stockDividendEnrichmentService;

    @MockBean
    private StockMetadataEnrichmentService stockMetadataEnrichmentService;

    @MockBean
    private StockIssuerProfileSyncService stockIssuerProfileSyncService;

    @MockBean
    private KrxLogoCollectorService krxLogoCollectorService;

    @MockBean
    private KisDomesticDividendDebugService kisDomesticDividendDebugService;

    @Test
    void metadataSyncEndpointRejectsInvalidSecret() throws Exception {
        mockMvc.perform(post("/internal/stock-enrichment/metadata/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Internal-Trigger-Secret", "wrong"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void dividendSyncEndpointRunsPrioritySyncWhenNoIdsProvided() throws Exception {
        mockMvc.perform(post("/internal/stock-enrichment/dividends/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Internal-Trigger-Secret", "test-ops-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("DIVIDEND"))
                .andExpect(jsonPath("$.data.mode").value("PRIORITY"))
                .andExpect(jsonPath("$.data.requestedCount").value(0));

        verify(stockDividendEnrichmentService).syncPrioritySymbols();
    }

    @Test
    void metadataSyncEndpointRunsExplicitSyncWhenIdsProvided() throws Exception {
        mockMvc.perform(post("/internal/stock-enrichment/metadata/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Internal-Trigger-Secret", "test-ops-secret")
                        .content("""
                                {
                                  "stockSymbolIds": [11, 22]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("METADATA"))
                .andExpect(jsonPath("$.data.mode").value("EXPLICIT"))
                .andExpect(jsonPath("$.data.requestedCount").value(2));

        verify(stockMetadataEnrichmentService).syncSymbols(java.util.List.of(11L, 22L));
    }

    @Test
    void issuerProfileSyncEndpointRunsPrioritySyncWhenNoIdsProvided() throws Exception {
        mockMvc.perform(post("/internal/stock-enrichment/issuer-profiles/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Internal-Trigger-Secret", "test-ops-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("ISSUER_PROFILE"))
                .andExpect(jsonPath("$.data.mode").value("PRIORITY"))
                .andExpect(jsonPath("$.data.requestedCount").value(0));

        verify(stockIssuerProfileSyncService).syncPrioritySymbols();
    }

    @Test
    void logoSyncEndpointRunsPrioritySyncWhenNoIdsProvided() throws Exception {
        mockMvc.perform(post("/internal/stock-enrichment/logos/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Internal-Trigger-Secret", "test-ops-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("BRANDING"))
                .andExpect(jsonPath("$.data.mode").value("PRIORITY"))
                .andExpect(jsonPath("$.data.requestedCount").value(0));

        verify(krxLogoCollectorService).syncPrioritySymbols();
    }

    @Test
    void kisDividendDebugEndpointReturnsCapturedPayloadSummary() throws Exception {
        org.mockito.BDDMockito.given(kisDomesticDividendDebugService.capture(org.mockito.ArgumentMatchers.any()))
                .willReturn(new KisDividendDebugResponse(
                        "005930",
                        "/tmp/folo-debug/kis-dividend/005930-20260322-213000.json",
                        12,
                        java.util.List.of("rt_cd", "msg_cd", "msg1", "output1"),
                        java.util.List.of("record_date", "per_sto_divi_amt", "divi_kind")
                ));

        mockMvc.perform(post("/internal/stock-enrichment/dividends/debug/kis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Internal-Trigger-Secret", "test-ops-secret")
                        .content("""
                                {
                                  "ticker": "005930",
                                  "fromDate": "2023-01-01"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticker").value("005930"))
                .andExpect(jsonPath("$.data.rowCount").value(12))
                .andExpect(jsonPath("$.data.savedPath").value("/tmp/folo-debug/kis-dividend/005930-20260322-213000.json"));

        verify(kisDomesticDividendDebugService).capture(org.mockito.ArgumentMatchers.any());
    }
}
