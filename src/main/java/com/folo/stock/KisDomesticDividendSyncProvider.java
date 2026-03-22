package com.folo.stock;

import com.fasterxml.jackson.databind.JsonNode;
import com.folo.common.enums.MarketType;
import com.folo.config.MarketDataSyncProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class KisDomesticDividendSyncProvider implements StockDividendSyncProvider {

    private static final DateTimeFormatter KIS_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String TRANSACTION_ID = "HHKDB669102C0";

    private final RestClient restClient;
    private final MarketDataSyncProperties properties;
    private final KisAccessTokenService accessTokenService;

    public KisDomesticDividendSyncProvider(
            RestClient.Builder restClientBuilder,
            MarketDataSyncProperties properties,
            KisAccessTokenService accessTokenService
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.accessTokenService = accessTokenService;
    }

    @Override
    public StockDataProvider provider() {
        return StockDataProvider.KIS;
    }

    @Override
    public boolean isConfigured() {
        return properties.kis().dividendEnabled() && accessTokenService.isConfigured();
    }

    @Override
    public boolean supports(MarketType market) {
        return market == MarketType.KRX;
    }

    @Override
    public List<DividendEventRecord> fetchEvents(StockSymbol stockSymbol, LocalDate fromDateInclusive) {
        JsonNode response = fetchRawPayload(
                stockSymbol.getTicker(),
                fromDateInclusive,
                LocalDate.now(),
                "0"
        );

        JsonNode rows = resolveRows(response);
        if (rows == null || !rows.isArray()) {
            return List.of();
        }

        List<DividendEventRecord> events = new ArrayList<>();
        for (JsonNode row : rows) {
            DividendEventRecord event = toEvent(stockSymbol, row, fromDateInclusive);
            if (event != null) {
                events.add(event);
            }
        }
        return events;
    }

    JsonNode fetchRawPayload(
            String ticker,
            LocalDate fromDateInclusive,
            LocalDate toDateInclusive,
            String highGb
    ) {
        URI requestUri = UriComponentsBuilder.fromUriString(properties.kis().baseUrl())
                .path("/uapi/domestic-stock/v1/ksdinfo/dividend")
                .queryParam("CTS", "")
                .queryParam("GB1", "0")
                .queryParam("F_DT", fromDateInclusive.format(KIS_DATE_FORMAT))
                .queryParam("T_DT", toDateInclusive.format(KIS_DATE_FORMAT))
                .queryParam("SHT_CD", ticker)
                .queryParam("HIGH_GB", StringUtils.hasText(highGb) ? highGb : "0")
                .build(true)
                .toUri();

        return restClient.get()
                .uri(requestUri)
                .headers(headers -> applyKisHeaders(headers, accessTokenService.getAccessToken()))
                .retrieve()
                .body(JsonNode.class);
    }

    private void applyKisHeaders(org.springframework.http.HttpHeaders headers, String accessToken) {
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("appkey", properties.kis().appKey());
        headers.set("appsecret", properties.kis().appSecret());
        headers.set("tr_id", TRANSACTION_ID);
        headers.set("custtype", "P");
    }

    private JsonNode resolveRows(JsonNode response) {
        if (response == null || response.isNull()) {
            return null;
        }

        JsonNode output1 = response.get("output1");
        if (output1 != null && output1.isArray()) {
            return output1;
        }

        JsonNode output = response.get("output");
        if (output != null && output.isArray()) {
            return output;
        }

        return null;
    }

    private DividendEventRecord toEvent(StockSymbol stockSymbol, JsonNode row, LocalDate fromDateInclusive) {
        LocalDate recordDate = parseDate(text(row, "record_date", "recordDate", "rdem_dt"));
        LocalDate payDate = parseDate(text(
                row,
                "divi_pay_dt",
                "stk_div_pay_dt",
                "odd_pay_dt",
                "dividend_pay_date",
                "payDate"
        ));
        LocalDate representativeDate = payDate != null ? payDate : recordDate;
        if (representativeDate != null && representativeDate.isBefore(fromDateInclusive)) {
            return null;
        }

        BigDecimal cashAmount = parseDecimal(text(
                row,
                "per_sto_divi_amt",
                "cash_dividend_per_share",
                "cash_amount"
        ));
        BigDecimal stockDividendRate = parseDecimal(text(row, "stk_divi_rate", "stock_dividend_rate"));
        String dividendKind = text(row, "divi_kind", "dividend_kind");

        DividendEventType eventType = resolveEventType(dividendKind, cashAmount, stockDividendRate);
        if (cashAmount != null && cashAmount.signum() == 0 && eventType != DividendEventType.CASH) {
            cashAmount = null;
        }

        if (representativeDate == null && cashAmount == null && eventType == DividendEventType.OTHER) {
            return null;
        }

        return new DividendEventRecord(
                provider(),
                buildSourceEventId(stockSymbol.getTicker(), recordDate, payDate, dividendKind),
                eventType,
                null,
                null,
                recordDate,
                payDate,
                cashAmount,
                "KRW",
                null
        );
    }

    private DividendEventType resolveEventType(
            String dividendKind,
            BigDecimal cashAmount,
            BigDecimal stockDividendRate
    ) {
        String normalizedKind = StringUtils.hasText(dividendKind) ? dividendKind.trim().toUpperCase() : "";
        if (normalizedKind.contains("주식") || normalizedKind.contains("STOCK")) {
            return DividendEventType.STOCK;
        }
        if (stockDividendRate != null && stockDividendRate.signum() > 0 && (cashAmount == null || cashAmount.signum() == 0)) {
            return DividendEventType.STOCK;
        }
        if (cashAmount != null && cashAmount.signum() > 0) {
            return DividendEventType.CASH;
        }
        return DividendEventType.OTHER;
    }

    private String buildSourceEventId(
            String ticker,
            LocalDate recordDate,
            LocalDate payDate,
            String dividendKind
    ) {
        String normalizedKind = StringUtils.hasText(dividendKind) ? dividendKind.trim() : "-";
        String normalizedRecordDate = recordDate == null ? "-" : recordDate.toString();
        String normalizedPayDate = payDate == null ? "-" : payDate.toString();
        return "%s|%s|%s|%s".formatted(ticker, normalizedRecordDate, normalizedPayDate, normalizedKind);
    }

    private String text(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode field = node.get(fieldName);
            if (field == null || field.isNull()) {
                continue;
            }

            String value = field.asText();
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private LocalDate parseDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }

        try {
            return LocalDate.parse(raw.trim(), KIS_DATE_FORMAT);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }

        String normalized = raw.trim().replace(",", "");
        if (!StringUtils.hasText(normalized) || "-".equals(normalized)) {
            return null;
        }

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
