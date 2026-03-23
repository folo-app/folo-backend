package com.folo.stock;

import com.folo.common.enums.MarketType;
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
    private final StockBrandAssetRepository stockBrandAssetRepository;
    private final StockSymbolRepository stockSymbolRepository;
    private final KrxBrandFamilyResolver krxBrandFamilyResolver;
    private final Map<String, BrandingAsset> brandingCache = new ConcurrentHashMap<>();
    private final Map<String, LogoPayload> logoCache = new ConcurrentHashMap<>();

    public StockBrandingService(
            RestClient.Builder restClientBuilder,
            MarketDataSyncProperties properties,
            StockBrandAssetRepository stockBrandAssetRepository,
            StockSymbolRepository stockSymbolRepository,
            KrxBrandFamilyResolver krxBrandFamilyResolver
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.stockBrandAssetRepository = stockBrandAssetRepository;
        this.stockSymbolRepository = stockSymbolRepository;
        this.krxBrandFamilyResolver = krxBrandFamilyResolver;
    }

    public String getPublicLogoUrl(StockSymbol stock) {
        String storedLogoUrl = resolveStoredLogoUrl(stock);
        if (StringUtils.hasText(storedLogoUrl)) {
            return storedLogoUrl;
        }

        if (!supportsBranding(stock.getMarket())) {
            return null;
        }

        StringBuilder publicUrl = new StringBuilder(
                "/stocks/%s/logo?market=%s".formatted(stock.getTicker(), stock.getMarket())
        );
        String micCode = resolveTwelveDataMicCode(stock);
        if (StringUtils.hasText(micCode)) {
            publicUrl.append("&micCode=").append(micCode);
        }
        return publicUrl.toString();
    }

    @Nullable
    private String resolveStoredLogoUrl(StockSymbol stock) {
        if (stock.getId() == null) {
            return null;
        }

        if (stock.getMarket() == MarketType.KRX) {
            String familyLogoUrl = resolveKrxFamilyLogoUrl(stock);
            if (StringUtils.hasText(familyLogoUrl)) {
                return familyLogoUrl;
            }

            String commonStockLogoUrl = resolvePreferredShareLogoUrl(stock);
            if (StringUtils.hasText(commonStockLogoUrl)) {
                return commonStockLogoUrl;
            }
        }

        return resolveDirectStoredLogoUrl(stock.getId());
    }

    @Nullable
    private String resolveKrxFamilyLogoUrl(StockSymbol stock) {
        KrxBrandFamilyResolver.KrxBrandFamily family = krxBrandFamilyResolver.resolve(stock);
        if (family == null) {
            return null;
        }

        List<StockSymbol> representativeCandidates = stockSymbolRepository.findByMarketAndTickerInAndActiveTrue(
                MarketType.KRX,
                family.representativeTickers()
        );
        if (representativeCandidates.isEmpty()) {
            return null;
        }

        Map<Long, StockBrandAsset> assetsBySymbolId = stockBrandAssetRepository.findAllByStockSymbolIdIn(
                representativeCandidates.stream().map(StockSymbol::getId).toList()
        ).stream().collect(java.util.stream.Collectors.toMap(
                asset -> asset.getStockSymbol().getId(),
                asset -> asset
        ));

        for (String representativeTicker : family.representativeTickers()) {
            StockSymbol representative = representativeCandidates.stream()
                    .filter(symbol -> representativeTicker.equalsIgnoreCase(symbol.getTicker()))
                    .findFirst()
                    .orElse(null);
            if (representative == null) {
                continue;
            }

            String representativeUrl = resolvePublicUrl(assetsBySymbolId.get(representative.getId()));
            if (StringUtils.hasText(representativeUrl)) {
                return representativeUrl;
            }
        }
        return null;
    }

    @Nullable
    private String resolvePreferredShareLogoUrl(StockSymbol stock) {
        return resolveCommonStock(stock)
                .map(StockSymbol::getId)
                .map(this::resolveDirectStoredLogoUrl)
                .filter(StringUtils::hasText)
                .orElse(null);
    }

    private Optional<StockSymbol> resolveCommonStock(StockSymbol stock) {
        String commonStockName = krxBrandFamilyResolver.resolveCommonStockName(stock.getName());
        if (!StringUtils.hasText(commonStockName)) {
            return Optional.empty();
        }
        return stockSymbolRepository.findByMarketAndName(MarketType.KRX, commonStockName);
    }

    @Nullable
    private String resolveDirectStoredLogoUrl(@Nullable Long stockSymbolId) {
        if (stockSymbolId == null) {
            return null;
        }
        return stockBrandAssetRepository.findByStockSymbolId(stockSymbolId)
                .map(this::resolvePublicUrl)
                .filter(StringUtils::hasText)
                .orElse(null);
    }

    @Nullable
    private String resolvePublicUrl(@Nullable StockBrandAsset asset) {
        if (asset == null || !StringUtils.hasText(asset.getPublicUrl())) {
            return null;
        }
        return asset.getPublicUrl();
    }

    public LogoPayload fetchLogo(String ticker, MarketType market) {
        return fetchLogo(ticker, market, null);
    }

    public LogoPayload fetchLogo(String ticker, MarketType market, String micCode) {
        if (!supportsBranding(market)) {
            throw new ResponseStatusException(NOT_FOUND, "브랜딩 로고를 찾을 수 없습니다.");
        }

        String cacheKey = "%s:%s:%s".formatted(
                market,
                ticker.toUpperCase(),
                micCode == null ? "" : micCode.toUpperCase()
        );
        LogoPayload cached = logoCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        BrandingAsset brandingAsset = brandingCache.computeIfAbsent(
                cacheKey,
                key -> resolveBrandingAsset(ticker, market, micCode)
        );
        String remoteUrl = brandingAsset.bestImageUrl();
        if (!StringUtils.hasText(remoteUrl)) {
            throw new ResponseStatusException(NOT_FOUND, "브랜딩 로고를 찾을 수 없습니다.");
        }

        URI requestUri = buildLogoDownloadUri(remoteUrl);

        ResponseEntity<byte[]> response = restClient.get()
                .uri(requestUri)
                .retrieve()
                .toEntity(byte[].class);

        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            throw new ResponseStatusException(NOT_FOUND, "브랜딩 로고를 찾을 수 없습니다.");
        }

        String contentType = response.getHeaders().getContentType() != null
                ? response.getHeaders().getContentType().toString()
                : "image/png";

        LogoPayload payload = new LogoPayload(body, contentType);
        logoCache.put(cacheKey, payload);
        return payload;
    }

    private boolean supportsBranding(MarketType market) {
        return isTwelveDataConfigured() || supportsPolygonFallback(market);
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
