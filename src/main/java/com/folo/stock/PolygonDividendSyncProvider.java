package com.folo.stock;

import com.folo.common.enums.MarketType;
import com.folo.config.MarketDataSyncProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class PolygonDividendSyncProvider implements StockDividendSyncProvider {

    private static final int MAX_PAGE_SIZE = 1000;

    private final RestClient restClient;
    private final MarketDataSyncProperties properties;

    public PolygonDividendSyncProvider(
            RestClient.Builder restClientBuilder,
            MarketDataSyncProperties properties
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public StockDataProvider provider() {
        return StockDataProvider.POLYGON;
    }

    @Override
    public boolean isConfigured() {
        return properties.polygon().dividendEnabled() && StringUtils.hasText(properties.polygon().apiKey());
    }

    @Override
    public boolean supports(MarketType market) {
        return market == MarketType.NASDAQ
                || market == MarketType.NYSE
                || market == MarketType.AMEX;
    }

    @Override
    public List<DividendEventRecord> fetchEvents(StockSymbol stockSymbol, LocalDate fromDateInclusive) {
        List<DividendEventRecord> records = new ArrayList<>();
        String cursor = null;
        boolean shouldContinue;

        do {
            URI requestUri = buildRequestUri(stockSymbol.getTicker(), cursor);
            PolygonDividendsResponse response = restClient.get()
                    .uri(requestUri)
                    .retrieve()
                    .body(PolygonDividendsResponse.class);

            if (response == null || response.results() == null || response.results().isEmpty()) {
                break;
            }

            shouldContinue = false;
            for (PolygonDividendResult result : response.results()) {
                LocalDate representativeDate = resolveRepresentativeDate(result);
                if (representativeDate != null && representativeDate.isBefore(fromDateInclusive)) {
                    continue;
                }

                records.add(new DividendEventRecord(
                        provider(),
                        result.id(),
                        resolveEventType(result.dividend_type()),
                        parseDate(result.declaration_date()),
                        parseDate(result.ex_dividend_date()),
                        parseDate(result.record_date()),
                        parseDate(result.pay_date()),
                        result.cash_amount(),
                        normalizeText(result.currency()),
                        result.frequency() == null ? null : Integer.toString(result.frequency())
                ));

                if (representativeDate == null || !representativeDate.isBefore(fromDateInclusive)) {
                    shouldContinue = true;
                }
            }

            cursor = response.next_url();
        } while (StringUtils.hasText(cursor) && shouldContinue);

        return records;
    }

    private URI buildRequestUri(String ticker, String cursor) {
        UriComponentsBuilder builder = StringUtils.hasText(cursor)
                ? UriComponentsBuilder.fromUriString(cursor)
                : UriComponentsBuilder.fromUriString(properties.polygon().baseUrl())
                .path("/v3/reference/dividends")
                .queryParam("ticker", ticker)
                .queryParam("order", "desc")
                .queryParam("sort", "ex_dividend_date")
                .queryParam("limit", MAX_PAGE_SIZE);

        return builder
                .replaceQueryParam("apiKey", properties.polygon().apiKey())
                .build(true)
                .toUri();
    }

    private DividendEventType resolveEventType(String rawDividendType) {
        if (!StringUtils.hasText(rawDividendType)) {
            return DividendEventType.CASH;
        }

        return switch (rawDividendType.trim().toUpperCase()) {
            case "SC" -> DividendEventType.SPECIAL_CASH;
            case "CD" -> DividendEventType.CASH;
            default -> DividendEventType.OTHER;
        };
    }

    private LocalDate resolveRepresentativeDate(PolygonDividendResult result) {
        LocalDate exDividendDate = parseDate(result.ex_dividend_date());
        if (exDividendDate != null) {
            return exDividendDate;
        }

        LocalDate payDate = parseDate(result.pay_date());
        if (payDate != null) {
            return payDate;
        }

        LocalDate recordDate = parseDate(result.record_date());
        if (recordDate != null) {
            return recordDate;
        }

        return parseDate(result.declaration_date());
    }

    private LocalDate parseDate(String raw) {
        return StringUtils.hasText(raw) ? LocalDate.parse(raw.trim()) : null;
    }

    private String normalizeText(String raw) {
        return StringUtils.hasText(raw) ? raw.trim().toUpperCase() : null;
    }

    private record PolygonDividendsResponse(
            List<PolygonDividendResult> results,
            String next_url
    ) {
    }

    private record PolygonDividendResult(
            String id,
            BigDecimal cash_amount,
            String currency,
            String declaration_date,
            String ex_dividend_date,
            Integer frequency,
            String pay_date,
            String record_date,
            String dividend_type
    ) {
    }
}
