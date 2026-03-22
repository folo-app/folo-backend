package com.folo.stock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KisDomesticDividendDebugServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void captureWritesRawPayloadAndReturnsSummary() throws Exception {
        KisDomesticDividendSyncProvider provider = mock(KisDomesticDividendSyncProvider.class);
        StockSymbolRepository stockSymbolRepository = mock(StockSymbolRepository.class);

        when(provider.isConfigured()).thenReturn(true);
        when(provider.fetchRawPayload(eq("005930"), eq(java.time.LocalDate.of(2023, 1, 1)), eq(java.time.LocalDate.of(2026, 3, 22)), eq("0")))
                .thenReturn(new ObjectMapper().readTree("""
                        {
                          "rt_cd": "0",
                          "msg_cd": "MCA00000",
                          "msg1": "정상처리 되었습니다.",
                          "output1": [
                            {
                              "record_date": "20250331",
                              "per_sto_divi_amt": "367",
                              "divi_kind": "분기"
                            }
                          ]
                        }
                        """));

        KisDomesticDividendDebugService service = new KisDomesticDividendDebugService(
                provider,
                stockSymbolRepository,
                new ObjectMapper(),
                tempDir
        );

        KisDividendDebugResponse response = service.capture(
                new KisDividendDebugRequest(
                        null,
                        "005930",
                        java.time.LocalDate.of(2023, 1, 1),
                        java.time.LocalDate.of(2026, 3, 22),
                        "0"
                )
        );

        Path savedPath = Path.of(response.savedPath());
        assertThat(savedPath).exists();
        assertThat(Files.readString(savedPath)).contains("\"output1\"");
        assertThat(response.ticker()).isEqualTo("005930");
        assertThat(response.rowCount()).isEqualTo(1);
        assertThat(response.topLevelKeys()).contains("rt_cd", "msg_cd", "msg1", "output1");
        assertThat(response.firstRowKeys()).contains("record_date", "per_sto_divi_amt", "divi_kind");
        verify(provider).fetchRawPayload(eq("005930"), eq(java.time.LocalDate.of(2023, 1, 1)), eq(java.time.LocalDate.of(2026, 3, 22)), eq("0"));
    }

    @Test
    void captureResolvesTickerFromStockSymbolId() throws Exception {
        KisDomesticDividendSyncProvider provider = mock(KisDomesticDividendSyncProvider.class);
        StockSymbolRepository stockSymbolRepository = mock(StockSymbolRepository.class);
        StockSymbol stockSymbol = new StockSymbol();
        stockSymbol.setId(7L);
        stockSymbol.setTicker("005930");
        stockSymbol.setName("삼성전자");
        stockSymbol.setMarket(MarketType.KRX);
        stockSymbol.setAssetType(AssetType.STOCK);
        stockSymbol.setActive(true);
        stockSymbol.setCurrencyCode("KRW");
        stockSymbol.setPrimaryExchangeCode("XKRX");
        stockSymbol.setSourceProvider(StockDataProvider.KIS);
        stockSymbol.setSourceIdentifier("005930");
        stockSymbol.setLastMasterSyncedAt(LocalDateTime.now());

        when(provider.isConfigured()).thenReturn(true);
        when(stockSymbolRepository.findById(7L)).thenReturn(Optional.of(stockSymbol));
        when(provider.fetchRawPayload(eq("005930"), any(java.time.LocalDate.class), any(java.time.LocalDate.class), eq((String) null)))
                .thenReturn(new ObjectMapper().readTree("{\"output1\": []}"));

        KisDomesticDividendDebugService service = new KisDomesticDividendDebugService(
                provider,
                stockSymbolRepository,
                new ObjectMapper(),
                tempDir
        );

        KisDividendDebugResponse response = service.capture(
                new KisDividendDebugRequest(7L, null, null, null, null)
        );

        assertThat(response.ticker()).isEqualTo("005930");
        verify(stockSymbolRepository).findById(7L);
    }
}
