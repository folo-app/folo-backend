package com.folo.stock;

import com.folo.common.enums.MarketType;
import com.folo.config.FileStorageProperties;
import com.folo.config.MarketDataSyncProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class StockBrandingService {

    private static final Logger log = LoggerFactory.getLogger(StockBrandingService.class);
    private final RestClient restClient;
    private final MarketDataSyncProperties properties;
    private final FileStorageProperties fileStorageProperties;
    private final StockSymbolRepository stockSymbolRepository;
    private final Map<String, BrandingAsset> brandingCache = new ConcurrentHashMap<>();
    private final Map<String, LogoPayload> logoCache = new ConcurrentHashMap<>();

    public StockBrandingService(
            RestClient.Builder restClientBuilder,
            MarketDataSyncProperties properties,
            FileStorageProperties fileStorageProperties,
            StockSymbolRepository stockSymbolRepository
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.fileStorageProperties = fileStorageProperties;
        this.stockSymbolRepository = stockSymbolRepository;
    }

    public String getPublicLogoUrl(StockSymbol stock) {
        return resolveLocalLogoPath(stock).isPresent()
                ? buildPublicLogoUrl(stock, null)
                : null;
    }

    public LogoPayload fetchLogo(String ticker, MarketType market) {
        return fetchLogo(ticker, market, null);
    }

    public LogoPayload fetchLogo(String ticker, MarketType market, String micCode) {
        return fetchLocalLogo(ticker, market);
    }

    private String buildPublicLogoUrl(StockSymbol stock, String micCode) {
        StringBuilder publicUrl = new StringBuilder(
                "/stocks/%s/logo?market=%s".formatted(stock.getTicker(), stock.getMarket())
        );
        if (StringUtils.hasText(micCode)) {
            publicUrl.append("&micCode=").append(micCode);
        }
        return publicUrl.toString();
    }

    private boolean supportsBranding(MarketType market) {
        return market != MarketType.KRX && (isTwelveDataConfigured() || supportsPolygonFallback(market));
    }

    private LogoPayload fetchLocalLogo(String ticker, MarketType market) {
        Path logoPath = resolveLocalLogoPath(
                ticker,
                findStockSymbol(market, ticker).orElse(null),
                market
        )
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "브랜딩 로고를 찾을 수 없습니다."));
        try {
            byte[] bytes = Files.readAllBytes(logoPath);
            if (bytes.length == 0) {
                throw new ResponseStatusException(NOT_FOUND, "브랜딩 로고를 찾을 수 없습니다.");
            }
            return new LogoPayload(bytes, resolveLocalContentType(logoPath));
        } catch (IOException exception) {
            throw new IllegalStateException("로컬 브랜딩 로고 파일을 읽을 수 없습니다: " + logoPath, exception);
        }
    }

    private Optional<Path> resolveLocalLogoPath(StockSymbol stock) {
        return resolveLocalLogoPath(stock.getTicker(), stock, stock.getMarket());
    }

    private Optional<Path> resolveLocalLogoPath(String ticker, @Nullable StockSymbol stock, @Nullable MarketType market) {
        if (!StringUtils.hasText(fileStorageProperties.stockLogoRootDir())
                || !StringUtils.hasText(ticker)) {
            return Optional.empty();
        }

        Path logoRoot = Path.of(fileStorageProperties.stockLogoRootDir())
                .toAbsolutePath()
                .normalize();
        List<String> tickerCandidates = resolveLocalLogoTickerCandidates(ticker, stock);

        for (String normalizedTicker : tickerCandidates) {
            for (String extension : new String[]{"png", "svg", "webp", "jpg", "jpeg"}) {
                Optional<Path> directMatch = existingFile(
                        logoRoot.resolve("%s.%s".formatted(normalizedTicker, extension))
                );
                if (directMatch.isPresent()) {
                    return directMatch;
                }

                Optional<Path> kospiMatch = existingFile(
                        logoRoot.resolve("KOSPI-logo").resolve("%s.%s".formatted(normalizedTicker, extension))
                );
                if (kospiMatch.isPresent()) {
                    return kospiMatch;
                }

                Optional<Path> kosdaqMatch = existingFile(
                        logoRoot.resolve("KOSDAQ-logo").resolve("%s.%s".formatted(normalizedTicker, extension))
                );
                if (kosdaqMatch.isPresent()) {
                    return kosdaqMatch;
                }

                if (isUsMarket(market)) {
                    Optional<Path> usLogoMatch = existingFile(
                            logoRoot.resolve("us-logo").resolve("%s.%s".formatted(normalizedTicker, extension))
                    );
                    if (usLogoMatch.isPresent()) {
                        return usLogoMatch;
                    }
                }
            }
        }

        return Optional.empty();
    }

    private List<String> resolveLocalLogoTickerCandidates(String ticker, @Nullable StockSymbol stock) {
        String normalizedTicker = ticker.trim().toUpperCase();
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(normalizedTicker);

        String baseTickerCandidate = KrxPreferredStockSupport.baseTickerCandidate(
                normalizedTicker,
                stock != null ? stock.getName() : null
        );
        if (StringUtils.hasText(baseTickerCandidate)) {
            candidates.add(baseTickerCandidate);
        }

        return List.copyOf(candidates);
    }

    private Optional<StockSymbol> findStockSymbol(MarketType market, String ticker) {
        return stockSymbolRepository.findByMarketAndTicker(market, ticker);
    }

    private boolean isKrxNumericPreferredTicker(String ticker, @Nullable StockSymbol stock) {
        return stock != null
                && stock.getMarket() == MarketType.KRX
                && ticker.matches("^\\d{6}$")
                && !ticker.endsWith("0")
                && KrxPreferredStockSupport.isPreferredStockName(stock.getName());
    }

    private boolean isPreferredStockName(@Nullable String name) {
        return KrxPreferredStockSupport.isPreferredStockName(name);
    }

    private boolean isUsMarket(@Nullable MarketType market) {
        return market == MarketType.NASDAQ
                || market == MarketType.NYSE
                || market == MarketType.AMEX;
    }

    private Optional<Path> existingFile(Path path) {
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    private String resolveLocalContentType(Path logoPath) throws IOException {
        String contentType = Files.probeContentType(logoPath);
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }

        String fileName = logoPath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (fileName.endsWith(".webp")) {
            return "image/webp";
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "image/png";
    }

    private BrandingAsset resolveBrandingAsset(String ticker, MarketType market, String micCode) {
        BrandingAsset twelveDataAsset = fetchBrandingFromTwelveData(ticker, market, micCode);
        if (twelveDataAsset.hasImage()) {
            return twelveDataAsset;
        }

        if (supportsPolygonFallback(market)) {
            BrandingAsset polygonAsset = fetchBrandingFromPolygon(ticker);
            if (polygonAsset.hasImage()) {
                return polygonAsset;
            }
        }

        return BrandingAsset.empty();
    }

    private boolean isTwelveDataConfigured() {
        return properties.twelveData() != null
                && properties.twelveData().logoEnabled()
                && StringUtils.hasText(properties.twelveData().apiKey())
                && StringUtils.hasText(properties.twelveData().baseUrl());
    }

    private boolean supportsPolygonFallback(MarketType market) {
        return market != MarketType.KRX
                && properties.polygon() != null
                && properties.polygon().logoEnabled()
                && StringUtils.hasText(properties.polygon().apiKey());
    }

    private BrandingAsset fetchBrandingFromPolygon(String ticker) {
        URI requestUri = UriComponentsBuilder.fromUriString(properties.polygon().baseUrl())
                .path("/v3/reference/tickers/{ticker}")
                .queryParam("apiKey", properties.polygon().apiKey())
                .buildAndExpand(ticker)
                .toUri();

        PolygonTickerOverviewResponse response = restClient.get()
                .uri(requestUri)
                .retrieve()
                .body(PolygonTickerOverviewResponse.class);

        if (response == null || response.results() == null || response.results().branding() == null) {
            return BrandingAsset.empty();
        }

        return new BrandingAsset(
                response.results().branding().icon_url(),
                response.results().branding().logo_url()
        );
    }

    private BrandingAsset fetchBrandingFromTwelveData(String ticker, MarketType market, String micCode) {
        if (!isTwelveDataConfigured()) {
            return BrandingAsset.empty();
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.twelveData().baseUrl())
                .path("/logo")
                .queryParam("symbol", ticker)
                .queryParam("apikey", properties.twelveData().apiKey());

        String resolvedMicCode = StringUtils.hasText(micCode) ? micCode : resolveTwelveDataMicCode(market);
        if (StringUtils.hasText(resolvedMicCode)) {
            builder.queryParam("mic_code", resolvedMicCode);
        }

        try {
            TwelveDataLogoResponse response = restClient.get()
                    .uri(builder.build(true).toUri())
                    .retrieve()
                    .body(TwelveDataLogoResponse.class);

            if (response == null || !StringUtils.hasText(response.url())) {
                return BrandingAsset.empty();
            }

            return new BrandingAsset(null, response.url());
        } catch (RuntimeException exception) {
            log.debug("Twelve Data logo lookup failed for {}:{} - {}", market, ticker, exception.getMessage());
            return BrandingAsset.empty();
        }
    }

    private URI buildLogoDownloadUri(String remoteUrl) {
        if (remoteUrl.contains("api.polygon.io")) {
            return UriComponentsBuilder.fromUriString(remoteUrl)
                    .replaceQueryParam("apiKey", properties.polygon().apiKey())
                    .build(true)
                    .toUri();
        }

        return UriComponentsBuilder.fromUriString(remoteUrl).build(true).toUri();
    }

    private String resolveTwelveDataMicCode(StockSymbol stock) {
        if (StringUtils.hasText(stock.getPrimaryExchangeCode())) {
            String normalized = stock.getPrimaryExchangeCode().trim().toUpperCase();
            return switch (normalized) {
                case "NAS", "XNAS" -> "XNAS";
                case "NYS", "XNYS" -> "XNYS";
                case "AMS", "XASE", "AMEX" -> "XASE";
                case "XKRX", "KRX" -> "XKRX";
                default -> resolveTwelveDataMicCode(stock.getMarket());
            };
        }

        return resolveTwelveDataMicCode(stock.getMarket());
    }

    private String resolveTwelveDataMicCode(MarketType market) {
        return switch (market) {
            case KRX -> "XKRX";
            case NASDAQ -> "XNAS";
            case NYSE -> "XNYS";
            case AMEX -> "XASE";
        };
    }

    public record LogoPayload(byte[] bytes, String contentType) {
    }

    private record BrandingAsset(String iconUrl, String logoUrl) {
        static BrandingAsset empty() {
            return new BrandingAsset(null, null);
        }

        boolean hasImage() {
            return StringUtils.hasText(iconUrl) || StringUtils.hasText(logoUrl);
        }

        String bestImageUrl() {
            if (StringUtils.hasText(iconUrl)) {
                return iconUrl;
            }
            return StringUtils.hasText(logoUrl) ? logoUrl : null;
        }
    }

    private record PolygonTickerOverviewResponse(PolygonTickerOverviewResult results) {
    }

    private record PolygonTickerOverviewResult(PolygonBranding branding) {
    }

    private record PolygonBranding(String icon_url, String logo_url) {
    }

    private record TwelveDataLogoResponse(
            Map<String, Object> meta,
            String url,
            String status,
            Integer code,
            String message
    ) {
    }
}
