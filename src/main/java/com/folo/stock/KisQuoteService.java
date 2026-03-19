package com.folo.stock;

import com.folo.common.enums.MarketType;
import com.folo.config.MarketDataSyncProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KisQuoteService {

    private static final Logger log = LoggerFactory.getLogger(KisQuoteService.class);
    private static final Duration QUOTE_CACHE_TTL = Duration.ofMinutes(2);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final RestClient restClient;
    private final MarketDataSyncProperties properties;
    private final KisAccessTokenService accessTokenService;
    private final Map<String, CachedQuote> quoteCache = new ConcurrentHashMap<>();

    public KisQuoteService(
            RestClient.Builder restClientBuilder,
            MarketDataSyncProperties properties,
            KisAccessTokenService accessTokenService
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.accessTokenService = accessTokenService;
    }

    public Map<Long, ResolvedStockQuote> fetchQuotes(List<StockSymbol> stocks) {
        if (stocks.isEmpty() || !accessTokenService.isConfigured()) {
            return Map.of();
        }

        String accessToken = accessTokenService.getAccessToken();
        Map<Long, ResolvedStockQuote> resolved = new LinkedHashMap<>();
        List<StockSymbol> domesticTargets = new ArrayList<>();
        Map<MarketType, List<StockSymbol>> overseasTargets = new EnumMap<>(MarketType.class);

        for (StockSymbol stock : stocks) {
            ResolvedStockQuote cached = getFreshQuote(stock);
            if (cached != null) {
                resolved.put(stock.getId(), cached);
                continue;
            }

            if (stock.getMarket() == MarketType.KRX) {
                domesticTargets.add(stock);
            } else {
                overseasTargets.computeIfAbsent(stock.getMarket(), ignored -> new ArrayList<>()).add(stock);
            }
        }

        for (StockSymbol stock : domesticTargets) {
            try {
                ResolvedStockQuote quote = fetchDomesticQuote(stock, accessToken);
                if (quote != null) {
                    resolved.put(stock.getId(), quote);
                    cacheQuote(quote);
                }
            } catch (RuntimeException exception) {
                log.warn("KIS domestic quote fetch failed for {}:{} - {}",
                        stock.getMarket(), stock.getTicker(), exception.getMessage());
            }
        }

        for (Map.Entry<MarketType, List<StockSymbol>> entry : overseasTargets.entrySet()) {
            List<StockSymbol> marketStocks = entry.getValue();
            for (int offset = 0; offset < marketStocks.size(); offset += 10) {
                List<StockSymbol> chunk = marketStocks.subList(offset, Math.min(offset + 10, marketStocks.size()));
                try {
                    Map<Long, ResolvedStockQuote> batchResult = fetchOverseasBatch(chunk, accessToken);
                    resolved.putAll(batchResult);
                    batchResult.values().forEach(this::cacheQuote);
                } catch (RuntimeException exception) {
                    log.warn("KIS overseas batch quote fetch failed for {} ({} items) - {}",
                            entry.getKey(), chunk.size(), exception.getMessage());
                }
            }
        }

        return resolved;
    }

    private ResolvedStockQuote fetchDomesticQuote(StockSymbol stock, String accessToken) {
        URI requestUri = UriComponentsBuilder.fromUriString(properties.kis().baseUrl())
                .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                .queryParam("FID_INPUT_ISCD", stock.getTicker())
                .build(true)
                .toUri();

        KisDomesticQuoteResponse response = restClient.get()
                .uri(requestUri)
                .headers(headers -> applyKisHeaders(headers, accessToken, "FHKST01010100"))
                .retrieve()
                .body(KisDomesticQuoteResponse.class);

        if (response == null || response.output() == null) {
            return null;
        }

        BigDecimal currentPrice = decimal(response.output().stck_prpr());
        BigDecimal dayReturn = resolveDomesticDayReturn(response.output());
        BigDecimal dayReturnRate = resolveDomesticDayReturnRate(response.output(), dayReturn);

        return new ResolvedStockQuote(
                stock,
                currentPrice,
                decimal(response.output().stck_oprc()),
                decimal(response.output().stck_hgpr()),
                decimal(response.output().stck_lwpr()),
                dayReturn,
                dayReturnRate,
                LocalDateTime.now()
        );
    }

    private Map<Long, ResolvedStockQuote> fetchOverseasBatch(List<StockSymbol> stocks, String accessToken) {
        URI requestUri = buildOverseasBatchUri(stocks);
        KisOverseasBatchQuoteResponse response = restClient.get()
                .uri(requestUri)
                .headers(headers -> applyKisHeaders(headers, accessToken, "HHDFS76220000"))
                .retrieve()
                .body(KisOverseasBatchQuoteResponse.class);

        if (response == null || response.output2() == null || response.output2().isEmpty()) {
            return Map.of();
        }

        Map<String, StockSymbol> stockByTicker = new LinkedHashMap<>();
        for (StockSymbol stock : stocks) {
            stockByTicker.put(stock.getTicker().toUpperCase(), stock);
        }

        Map<Long, ResolvedStockQuote> resolved = new LinkedHashMap<>();
        for (KisOverseasQuoteOutput item : response.output2()) {
            if (!StringUtils.hasText(item.symb())) {
                continue;
            }

            StockSymbol stock = stockByTicker.get(item.symb().trim().toUpperCase());
            if (stock == null) {
                continue;
            }

            BigDecimal currentPrice = decimal(item.last());
            BigDecimal basePrice = decimal(item.base());
            BigDecimal dayReturn = decimal(item.diff());
            if (dayReturn.signum() == 0 && basePrice.signum() != 0) {
                dayReturn = currentPrice.subtract(basePrice);
            }

            BigDecimal dayReturnRate = decimal(item.rate());
            if (dayReturnRate.signum() == 0 && basePrice.signum() != 0) {
                dayReturnRate = dayReturn.multiply(ONE_HUNDRED)
                        .divide(basePrice, 4, RoundingMode.HALF_UP);
            }

            resolved.put(stock.getId(), new ResolvedStockQuote(
                    stock,
                    currentPrice,
                    decimal(item.open()),
                    decimal(item.high()),
                    decimal(item.low()),
                    dayReturn,
                    dayReturnRate,
                    LocalDateTime.now()
            ));
        }

        return resolved;
    }

    private URI buildOverseasBatchUri(List<StockSymbol> stocks) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.kis().baseUrl())
                .path("/uapi/overseas-price/v1/quotations/multprice")
                .queryParam("AUTH", "")
                .queryParam("NREC", stocks.size());

        for (int index = 0; index < stocks.size(); index++) {
            StockSymbol stock = stocks.get(index);
            int sequence = index + 1;
            builder.queryParam("EXCD_%02d".formatted(sequence), resolveOverseasExchangeCode(stock.getMarket()));
            builder.queryParam("SYMB_%02d".formatted(sequence), stock.getTicker());
        }

        return builder.build(true).toUri();
    }

    private void applyKisHeaders(org.springframework.http.HttpHeaders headers, String accessToken, String transactionId) {
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("appkey", properties.kis().appKey());
        headers.set("appsecret", properties.kis().appSecret());
        headers.set("tr_id", transactionId);
        headers.set("custtype", "P");
    }

    private ResolvedStockQuote getFreshQuote(StockSymbol stock) {
        CachedQuote cached = quoteCache.get(cacheKey(stock));
        if (cached == null || cached.expiresAt().isBefore(Instant.now())) {
            return null;
        }

        return cached.quote();
    }

    private void cacheQuote(ResolvedStockQuote quote) {
        quoteCache.put(
                cacheKey(quote.stockSymbol()),
                new CachedQuote(quote, Instant.now().plus(QUOTE_CACHE_TTL))
        );
    }

    private String cacheKey(StockSymbol stock) {
        return stock.getMarket() + ":" + stock.getTicker().toUpperCase();
    }

    private BigDecimal resolveDomesticDayReturn(KisDomesticQuoteOutput output) {
        BigDecimal raw = decimal(output.prdy_vrss());
        String sign = output.prdy_vrss_sign() == null ? "" : output.prdy_vrss_sign().trim();
        return switch (sign) {
            case "4", "5" -> raw.negate();
            case "3" -> BigDecimal.ZERO;
            default -> raw;
        };
    }

    private BigDecimal resolveDomesticDayReturnRate(KisDomesticQuoteOutput output, BigDecimal dayReturn) {
        BigDecimal raw = decimal(output.prdy_ctrt());
        if (raw.signum() == 0) {
            return BigDecimal.ZERO;
        }

        return dayReturn.signum() < 0 && raw.signum() > 0 ? raw.negate() : raw;
    }

    private String resolveOverseasExchangeCode(MarketType market) {
        return switch (market) {
            case NASDAQ -> "NAS";
            case NYSE -> "NYS";
            case AMEX -> "AMS";
            default -> throw new IllegalArgumentException("Unsupported overseas market: " + market);
        };
    }

    private BigDecimal decimal(String raw) {
        if (!StringUtils.hasText(raw)) {
            return BigDecimal.ZERO;
        }

        String normalized = raw.trim()
                .replace(",", "")
                .replace("+", "");
        if (!StringUtils.hasText(normalized) || "-".equals(normalized)) {
            return BigDecimal.ZERO;
        }

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private record CachedQuote(ResolvedStockQuote quote, Instant expiresAt) {
    }

    private record KisDomesticQuoteResponse(KisDomesticQuoteOutput output) {
    }

    private record KisDomesticQuoteOutput(
            String stck_prpr,
            String stck_oprc,
            String stck_hgpr,
            String stck_lwpr,
            String prdy_vrss,
            String prdy_ctrt,
            String prdy_vrss_sign
    ) {
    }

    private record KisOverseasBatchQuoteResponse(List<KisOverseasQuoteOutput> output2) {
    }

    private record KisOverseasQuoteOutput(
            String symb,
            String last,
            String diff,
            String rate,
            String open,
            String high,
            String low,
            String base
    ) {
    }
}
