package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import com.folo.config.FileStorageProperties;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class KrxLogoCollectorService {

    private static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/134.0.0.0 Safari/537.36";
    private static final String BROWSER_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8";
    private static final String IMAGE_ACCEPT =
            "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8";
    private static final String ACCEPT_LANGUAGE =
            "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7";
    private static final Pattern JS_LOCATION_HREF_PATTERN = Pattern.compile(
            "location\\.(?:href|replace)\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern META_REFRESH_URL_PATTERN = Pattern.compile(
            "url=['\"]?([^'\";]+)['\"]?",
            Pattern.CASE_INSENSITIVE
    );

    private static final List<String> COMMON_LOGO_PATHS = List.of(
            "favicon.ico",
            "/favicon.ico",
            "apple-touch-icon.png",
            "/apple-touch-icon.png",
            "apple-touch-icon-precomposed.png",
            "/apple-touch-icon-precomposed.png",
            "logo.svg",
            "/logo.svg",
            "logo.png",
            "/logo.png",
            "img/logo.svg",
            "/img/logo.svg",
            "img/logo.png",
            "/img/logo.png",
            "images/logo.svg",
            "/images/logo.svg",
            "images/logo.png",
            "/images/logo.png",
            "assets/logo.svg",
            "/assets/logo.svg",
            "assets/logo.png",
            "/assets/logo.png"
    );
    private static final List<String> CANDIDATE_PAGE_KEYWORDS = List.of(
            "logo",
            "brand",
            "ci",
            "identity",
            "about",
            "company",
            "corporate",
            "invest",
            "investor",
            "investors",
            "ir"
    );
    private static final List<String> LOGO_HINT_KEYWORDS = List.of(
            "logo",
            "brand",
            "ci",
            "identity",
            "symbol"
    );
    private static final int MAX_PAGE_CANDIDATES = 8;
    private static final Map<String, List<String>> KNOWN_CANDIDATE_PAGES = Map.of(
            "005490", List.of("https://www.posco-inc.com/poscoinc/v4/kor/company/s91e1000400c.jsp"),
            "180640", List.of("https://www.hanjinkal.co.kr/hanjinkal/about-us/ci"),
            "267250", List.of("https://www.hd.com/kr/brand-story/typeface/contents"),
            "005440", List.of("https://m.ehyundai.com/newPortal/group/GI/GI000001.do?locale=ko"),
            "069960", List.of("https://m.ehyundai.com/newPortal/group/GI/GI000001.do?locale=ko"),
            "086520", List.of("https://www.ecopro.co.kr/sub010104")
    );
    private static final Map<String, List<DirectAssetOverride>> KNOWN_DIRECT_ASSETS = Map.of(
            "267250", List.of(new DirectAssetOverride(
                    "https://www.hd.com/common/kr/images/icon-brand-ci-logo.svg",
                    "https://www.hd.com/kr/brand-story/typeface/contents",
                    StockBrandAssetSourceType.LOGO_IMAGE
            )),
            "005440", List.of(new DirectAssetOverride(
                    "https://www.ehyundai.com/images/group/ko/hlds_07_ci.png",
                    "https://m.ehyundai.com/newPortal/group/GI/GI000001.do?locale=ko",
                    StockBrandAssetSourceType.LOGO_IMAGE
            )),
            "086520", List.of(new DirectAssetOverride(
                    "https://www.ecopro.co.kr/web/images/layout/logo.svg",
                    "https://www.ecopro.co.kr/",
                    StockBrandAssetSourceType.LOGO_IMAGE
            )),
            "005490", List.of(new DirectAssetOverride(
                    "https://www.posco-inc.com/poscoinc/v4/common/images/og-image.png",
                    "https://www.posco-inc.com/poscoinc/v4/kor/company/s91e1000400c.jsp",
                    StockBrandAssetSourceType.OG_IMAGE
            ))
    );

    private final RestClient restClient;
    private final FileStorageProperties fileStorageProperties;
    private final StockBrandAssetRepository stockBrandAssetRepository;
    private final StockIssuerProfileRepository stockIssuerProfileRepository;
    private final StockSymbolRepository stockSymbolRepository;
    private final StockSymbolSyncRunRepository stockSymbolSyncRunRepository;
    private final StockEnrichmentTargetSelector stockEnrichmentTargetSelector;

    public KrxLogoCollectorService(
            RestClient.Builder restClientBuilder,
            FileStorageProperties fileStorageProperties,
            StockBrandAssetRepository stockBrandAssetRepository,
            StockIssuerProfileRepository stockIssuerProfileRepository,
            StockSymbolRepository stockSymbolRepository,
            StockSymbolSyncRunRepository stockSymbolSyncRunRepository,
            StockEnrichmentTargetSelector stockEnrichmentTargetSelector
    ) {
        this.restClient = restClientBuilder.build();
        this.fileStorageProperties = fileStorageProperties;
        this.stockBrandAssetRepository = stockBrandAssetRepository;
        this.stockIssuerProfileRepository = stockIssuerProfileRepository;
        this.stockSymbolRepository = stockSymbolRepository;
        this.stockSymbolSyncRunRepository = stockSymbolSyncRunRepository;
        this.stockEnrichmentTargetSelector = stockEnrichmentTargetSelector;
    }

    public void syncPrioritySymbols() {
        List<StockSymbol> targets = stockEnrichmentTargetSelector.resolvePrioritySymbols(List.of(MarketType.KRX)).stream()
                .filter(this::isTarget)
                .toList();
        if (targets.isEmpty()) {
            targets = stockSymbolRepository.findActiveStocksByMarket(MarketType.KRX, PageRequest.of(0, 100));
        }
        syncSymbolsInternal(targets);
    }

    public void syncSymbols(Collection<Long> stockSymbolIds) {
        if (stockSymbolIds == null || stockSymbolIds.isEmpty()) {
            return;
        }

        Map<Long, StockSymbol> symbolsById = stockSymbolRepository.findAllById(stockSymbolIds).stream()
                .filter(this::isTarget)
                .collect(Collectors.toMap(StockSymbol::getId, symbol -> symbol, (left, right) -> left, LinkedHashMap::new));

        syncSymbolsInternal(stockSymbolIds.stream()
                .map(symbolsById::get)
                .filter(symbol -> symbol != null)
                .toList());
    }

    private void syncSymbolsInternal(List<StockSymbol> symbols) {
        if (symbols.isEmpty()) {
            return;
        }

        StockSymbolSyncRun syncRun = new StockSymbolSyncRun();
        syncRun.setProvider(StockDataProvider.OPENDART);
        syncRun.setMarket(MarketType.KRX);
        syncRun.setSyncScope(StockSymbolSyncScope.BRANDING);
        syncRun.setStatus(StockSymbolSyncStatus.STARTED);
        syncRun.setStartedAt(LocalDateTime.now());
        stockSymbolSyncRunRepository.save(syncRun);

        int fetchedCount = 0;
        int upsertedCount = 0;
        int failureCount = 0;
        List<String> failures = new ArrayList<>();

        for (StockSymbol symbol : symbols) {
            try {
                Optional<StockIssuerProfile> issuerProfile = stockIssuerProfileRepository.findByStockSymbolIdAndProvider(
                        symbol.getId(),
                        StockDataProvider.OPENDART
                );
                if (issuerProfile.isEmpty()) {
                    continue;
                }

                LogoCollectionResult result = collectLogo(symbol, issuerProfile.get());
                if (result == null) {
                    continue;
                }

                fetchedCount++;
                upsertedCount += upsertBrandAsset(symbol, result);
            } catch (RuntimeException exception) {
                failureCount++;
                if (failures.size() < 5) {
                    failures.add(symbol.getTicker() + ": " + exception.getMessage());
                }
            }
        }

        syncRun.setFetchedCount(fetchedCount);
        syncRun.setUpsertedCount(upsertedCount);
        syncRun.setDeactivatedCount(0);
        syncRun.setCompletedAt(LocalDateTime.now());
        if (failureCount > 0) {
            syncRun.setStatus(StockSymbolSyncStatus.FAILED);
            syncRun.setErrorMessage("failed symbols=%d; %s".formatted(failureCount, String.join(" | ", failures)));
        } else {
            syncRun.setStatus(StockSymbolSyncStatus.COMPLETED);
        }
        stockSymbolSyncRunRepository.save(syncRun);
    }

    LogoCollectionResult collectLogo(StockSymbol stockSymbol, StockIssuerProfile issuerProfile) {
        for (LogoCandidate candidate : resolveKnownDirectAssetCandidates(stockSymbol)) {
            LogoBinary binary = materializeBinary(candidate);
            if (binary == null) {
                continue;
            }
            try {
                return storeBinary(stockSymbol, candidate, binary);
            } catch (IOException exception) {
                return null;
            }
        }

        LinkedHashSet<String> candidatePages = Stream.concat(
                        resolveKnownCandidatePages(stockSymbol).stream(),
                        Stream.of(issuerProfile.getHmUrl(), issuerProfile.getIrUrl())
                )
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        LinkedHashSet<String> visitedPages = new LinkedHashSet<>();
        while (!candidatePages.isEmpty() && visitedPages.size() < MAX_PAGE_CANDIDATES) {
            String pageUrl = candidatePages.iterator().next();
            candidatePages.remove(pageUrl);
            if (!visitedPages.add(pageUrl)) {
                continue;
            }

            PageExtraction extraction = fetchPage(pageUrl);
            if (extraction == null) {
                continue;
            }

            LogoCollectionResult result = collectFromPage(stockSymbol, extraction.document(), extraction.pageUrl());
            if (result != null) {
                return result;
            }

            resolveCandidatePages(extraction.document(), extraction.pageUrl()).stream()
                    .filter(candidate -> !visitedPages.contains(candidate))
                    .limit(MAX_PAGE_CANDIDATES - visitedPages.size())
                    .forEach(candidatePages::add);
        }
        return null;
    }

    PageExtraction fetchPage(String pageUrl) {
        return fetchPage(pageUrl, 0);
    }

    @Nullable
    private PageExtraction fetchPage(String pageUrl, int depth) {
        if (depth > 2 || !StringUtils.hasText(pageUrl)) {
            return null;
        }
        try {
            Document document = Jsoup.connect(pageUrl)
                    .userAgent(BROWSER_USER_AGENT)
                    .header("Accept", BROWSER_ACCEPT)
                    .header("Accept-Language", ACCEPT_LANGUAGE)
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")
                    .ignoreContentType(true)
                    .timeout(10_000)
                    .get();

            String redirectedUrl = resolveDocumentRedirect(document, pageUrl);
            if (StringUtils.hasText(redirectedUrl) && !pageUrl.equalsIgnoreCase(redirectedUrl)) {
                return fetchPage(redirectedUrl, depth + 1);
            }

            return new PageExtraction(document, pageUrl);
        } catch (IOException exception) {
            return null;
        }
    }

    LogoCollectionResult collectFromPage(StockSymbol stockSymbol, Document document, String pageUrl) {
        List<LogoCandidate> candidates = resolveCandidates(document, pageUrl);
        for (LogoCandidate candidate : candidates) {
            LogoBinary binary = materializeBinary(candidate);
            if (binary != null) {
                try {
                    return storeBinary(stockSymbol, candidate, binary);
                } catch (IOException exception) {
                    return null;
                }
            }
        }
        return null;
    }

    List<LogoCandidate> resolveCandidates(Document document, String baseUrl) {
        List<LogoCandidate> candidates = new ArrayList<>();

        String ogImage = contentOf(document.selectFirst(
                "meta[property=og:image], meta[name=og:image], " +
                        "meta[property=og:image:secure_url], meta[itemprop=image]"
        ));
        if (StringUtils.hasText(ogImage)) {
            candidates.add(LogoCandidate.remote(
                    resolveUrl(baseUrl, ogImage),
                    StockBrandAssetSourceType.OG_IMAGE,
                    20,
                    baseUrl
            ));
        }

        String twitterImage = contentOf(document.selectFirst("meta[property=twitter:image], meta[name=twitter:image]"));
        if (StringUtils.hasText(twitterImage)) {
            candidates.add(LogoCandidate.remote(
                    resolveUrl(baseUrl, twitterImage),
                    StockBrandAssetSourceType.OG_IMAGE,
                    21,
                    baseUrl
            ));
        }

        Elements iconLinks = document.select("link[rel]");
        for (Element link : iconLinks) {
            String rel = link.attr("rel").toLowerCase(Locale.ROOT);
            if (!rel.contains("icon")) {
                continue;
            }
            String href = link.attr("href");
            if (StringUtils.hasText(href)) {
                StockBrandAssetSourceType sourceType = rel.contains("apple-touch-icon")
                        ? StockBrandAssetSourceType.APPLE_TOUCH_ICON
                        : StockBrandAssetSourceType.FAVICON;
                int priority = sourceType == StockBrandAssetSourceType.APPLE_TOUCH_ICON ? 30 : 40;
                candidates.add(LogoCandidate.remote(resolveUrl(baseUrl, href), sourceType, priority, baseUrl));
            }
        }

        for (Element image : document.select("img, source")) {
            if (!looksLikeLogoImage(image)) {
                continue;
            }
            resolveImageUrls(image).forEach(url -> candidates.add(LogoCandidate.remote(
                    resolveUrl(baseUrl, url),
                    StockBrandAssetSourceType.LOGO_IMAGE,
                    10,
                    baseUrl
            )));
        }

        for (Element svg : document.select("svg")) {
            if (!looksLikeLogoSvg(svg)) {
                continue;
            }
            candidates.add(LogoCandidate.inline(
                    inlineSvgKey(baseUrl, svg),
                    svg.outerHtml().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    "image/svg+xml",
                    StockBrandAssetSourceType.LOGO_IMAGE,
                    11
            ));
        }

        for (Element element : document.select("[style*=background-image], [style*=background:url], [class*=logo], [id*=logo]")) {
            if (!looksLikeBackgroundLogo(element)) {
                continue;
            }
            resolveStyleImageUrls(element.attr("style")).forEach(url -> candidates.add(LogoCandidate.remote(
                    resolveUrl(baseUrl, url),
                    StockBrandAssetSourceType.LOGO_IMAGE,
                    12,
                    baseUrl
            )));
        }

        resolveCommonPathCandidates(baseUrl).forEach(candidates::add);

        LinkedHashMap<String, LogoCandidate> deduped = new LinkedHashMap<>();
        for (LogoCandidate candidate : candidates) {
            if (StringUtils.hasText(candidate.cacheKey())) {
                deduped.putIfAbsent(candidate.cacheKey(), candidate);
            }
        }
        return deduped.values().stream()
                .sorted(Comparator.comparingInt(LogoCandidate::priority))
                .toList();
    }

    LogoBinary materializeBinary(LogoCandidate candidate) {
        if (candidate.inlineBytes() != null && candidate.inlineContentType() != null) {
            return new LogoBinary(candidate.inlineBytes(), candidate.inlineContentType());
        }
        if (!StringUtils.hasText(candidate.downloadUrl())) {
            return null;
        }
        return downloadBinary(candidate.downloadUrl(), candidate.referer());
    }

    LogoBinary downloadBinary(String url, @Nullable String referer) {
        try {
            ResponseEntity<byte[]> response = restClient.get()
                    .uri(URI.create(url))
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept", IMAGE_ACCEPT)
                    .header("Accept-Language", ACCEPT_LANGUAGE)
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")
                    .headers(headers -> {
                        if (StringUtils.hasText(referer)) {
                            headers.set("Referer", referer);
                        }
                    })
                    .retrieve()
                    .toEntity(byte[].class);

            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                return null;
            }

            String contentType = response.getHeaders().getContentType() != null
                    ? response.getHeaders().getContentType().toString()
                    : URLConnection.guessContentTypeFromName(url);
            String normalizedContentType = normalizeContentType(url, contentType, body);
            if (!StringUtils.hasText(normalizedContentType)) {
                return null;
            }

            return new LogoBinary(body, normalizedContentType);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    LogoCollectionResult storeBinary(
            StockSymbol stockSymbol,
            LogoCandidate candidate,
            LogoBinary binary
    ) throws IOException {
        String extension = resolveExtension(candidate.sourceRef(), binary.contentType());
        String fileName = stockSymbol.getTicker() + "-" + shortHash(candidate.sourceRef()) + "." + extension;
        Path targetDirectory = Paths.get(fileStorageProperties.uploadRootDir(), "stock-logos", "krx")
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(targetDirectory);

        Path tempFile = targetDirectory.resolve(UUID.randomUUID() + ".tmp");
        Files.write(tempFile, binary.bytes());
        Path finalPath = targetDirectory.resolve(fileName);
        Files.move(tempFile, finalPath, StandardCopyOption.REPLACE_EXISTING);

        return new LogoCollectionResult(
                candidate.sourceType(),
                candidate.sourceRef(),
                finalPath.toString(),
                "/uploads/stock-logos/krx/" + fileName,
                binary.contentType()
        );
    }

    private int upsertBrandAsset(StockSymbol stockSymbol, LogoCollectionResult result) {
        StockBrandAsset asset = stockBrandAssetRepository.findByStockSymbolId(stockSymbol.getId())
                .orElseGet(StockBrandAsset::new);
        asset.setStockSymbol(stockSymbol);
        asset.setProvider(StockDataProvider.OPENDART);
        asset.setSourceType(result.sourceType());
        asset.setSourceUrl(result.sourceUrl());
        asset.setStoragePath(result.storagePath());
        asset.setPublicUrl(result.publicUrl());
        asset.setContentType(result.contentType());
        asset.setLastSyncedAt(LocalDateTime.now());
        stockBrandAssetRepository.save(asset);
        return 1;
    }

    private boolean isTarget(StockSymbol stockSymbol) {
        return stockSymbol.getMarket() == MarketType.KRX
                && stockSymbol.getAssetType() == AssetType.STOCK
                && stockSymbol.isActive();
    }

    private String contentOf(Element element) {
        if (element == null) {
            return null;
        }
        String content = element.attr("content");
        return StringUtils.hasText(content) ? content.trim() : null;
    }

    List<String> resolveCandidatePages(Document document, String baseUrl) {
        LinkedHashSet<String> pages = new LinkedHashSet<>();
        for (Element link : document.select("a[href]")) {
            String href = link.attr("href");
            if (!StringUtils.hasText(href)) {
                continue;
            }
            String candidate = resolveUrl(baseUrl, href);
            if (!isCandidatePage(baseUrl, candidate, link.text())) {
                continue;
            }
            pages.add(candidate);
            if (pages.size() >= MAX_PAGE_CANDIDATES) {
                break;
            }
        }
        return new ArrayList<>(pages);
    }

    private boolean isCandidatePage(String baseUrl, String candidateUrl, String linkText) {
        if (!StringUtils.hasText(candidateUrl) || candidateUrl.equals(baseUrl)) {
            return false;
        }
        try {
            URL base = URI.create(baseUrl).toURL();
            URL candidate = URI.create(candidateUrl).toURL();
            if (!base.getHost().equalsIgnoreCase(candidate.getHost())) {
                return false;
            }
        } catch (MalformedURLException | IllegalArgumentException exception) {
            return false;
        }

        String lowered = (candidateUrl + " " + linkText).toLowerCase(Locale.ROOT);
        if (lowered.endsWith(".pdf") || lowered.endsWith(".zip")) {
            return false;
        }
        return CANDIDATE_PAGE_KEYWORDS.stream().anyMatch(lowered::contains);
    }

    List<LogoCandidate> resolveCommonPathCandidates(String baseUrl) {
        List<LogoCandidate> candidates = new ArrayList<>();
        for (String path : COMMON_LOGO_PATHS) {
            StockBrandAssetSourceType sourceType = path.contains("apple-touch-icon")
                    ? StockBrandAssetSourceType.APPLE_TOUCH_ICON
                    : path.contains("logo")
                    ? StockBrandAssetSourceType.LOGO_IMAGE
                    : StockBrandAssetSourceType.FAVICON;
            int priority = sourceType == StockBrandAssetSourceType.LOGO_IMAGE ? 25
                    : sourceType == StockBrandAssetSourceType.APPLE_TOUCH_ICON ? 30
                    : 40;
            candidates.add(LogoCandidate.remote(resolveUrl(baseUrl, path), sourceType, priority, baseUrl));
        }
        return candidates;
    }

    boolean looksLikeLogoImage(Element image) {
        String signal = String.join(" ",
                image.attr("src"),
                image.attr("srcset"),
                image.attr("data-src"),
                image.attr("data-original"),
                image.attr("data-lazy-src"),
                image.attr("class"),
                image.attr("id"),
                image.attr("alt"),
                image.parent() != null ? image.parent().className() : "",
                image.parent() != null ? image.parent().id() : ""
        ).toLowerCase(Locale.ROOT);
        if (LOGO_HINT_KEYWORDS.stream().anyMatch(signal::contains)) {
            return true;
        }
        return isInsideHeaderOrFooter(image);
    }

    boolean looksLikeLogoSvg(Element svg) {
        String signal = String.join(" ",
                svg.attr("class"),
                svg.attr("id"),
                svg.attr("aria-label"),
                svg.attr("title"),
                svg.parent() != null ? svg.parent().className() : "",
                svg.parent() != null ? svg.parent().id() : ""
        ).toLowerCase(Locale.ROOT);
        if (LOGO_HINT_KEYWORDS.stream().anyMatch(signal::contains)) {
            return true;
        }
        return isInsideHeaderOrFooter(svg);
    }

    boolean looksLikeBackgroundLogo(Element element) {
        String signal = String.join(" ",
                element.attr("style"),
                element.attr("class"),
                element.attr("id"),
                element.attr("aria-label"),
                element.attr("title")
        ).toLowerCase(Locale.ROOT);
        if (LOGO_HINT_KEYWORDS.stream().anyMatch(signal::contains)) {
            return true;
        }
        return isInsideHeaderOrFooter(element);
    }

    boolean isInsideHeaderOrFooter(Element element) {
        for (Element current = element; current != null; current = current.parent()) {
            String tagName = current.tagName().toLowerCase(Locale.ROOT);
            if ("header".equals(tagName) || "footer".equals(tagName)) {
                return true;
            }
        }
        return false;
    }

    String inlineSvgKey(String baseUrl, Element svg) {
        return baseUrl + "#inline-svg-" + shortHash(svg.outerHtml());
    }

    private List<String> resolveKnownCandidatePages(StockSymbol stockSymbol) {
        return KNOWN_CANDIDATE_PAGES.getOrDefault(stockSymbol.getTicker(), List.of());
    }

    private List<LogoCandidate> resolveKnownDirectAssetCandidates(StockSymbol stockSymbol) {
        return KNOWN_DIRECT_ASSETS.getOrDefault(stockSymbol.getTicker(), List.of()).stream()
                .map(override -> LogoCandidate.remote(
                        override.url(),
                        override.sourceType(),
                        5,
                        override.referer()
                ))
                .toList();
    }

    private String resolveDocumentRedirect(Document document, String pageUrl) {
        String metaRefresh = resolveMetaRefreshUrl(document, pageUrl);
        if (StringUtils.hasText(metaRefresh)) {
            return metaRefresh;
        }
        return resolveScriptRedirectUrl(document, pageUrl);
    }

    @Nullable
    private String resolveMetaRefreshUrl(Document document, String pageUrl) {
        Element metaRefresh = document.selectFirst("meta[http-equiv=refresh]");
        if (metaRefresh == null) {
            return null;
        }

        Matcher matcher = META_REFRESH_URL_PATTERN.matcher(metaRefresh.attr("content"));
        if (!matcher.find()) {
            return null;
        }
        return resolveUrl(pageUrl, matcher.group(1));
    }

    @Nullable
    private String resolveScriptRedirectUrl(Document document, String pageUrl) {
        for (Element script : document.select("script")) {
            String scriptBody = script.data();
            if (!StringUtils.hasText(scriptBody)) {
                scriptBody = script.html();
            }
            Matcher matcher = JS_LOCATION_HREF_PATTERN.matcher(scriptBody);
            if (matcher.find()) {
                return resolveUrl(pageUrl, matcher.group(1));
            }
        }
        return null;
    }

    private List<String> resolveImageUrls(Element image) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        addIfPresent(urls, image.attr("src"));
        addIfPresent(urls, image.attr("data-src"));
        addIfPresent(urls, image.attr("data-original"));
        addIfPresent(urls, image.attr("data-lazy-src"));
        parseSrcset(image.attr("srcset")).forEach(urls::add);
        return new ArrayList<>(urls);
    }

    private List<String> resolveStyleImageUrls(String style) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        if (!StringUtils.hasText(style)) {
            return List.of();
        }
        Matcher matcher = Pattern.compile("url\\((['\"]?)([^)'\"]+)\\1\\)", Pattern.CASE_INSENSITIVE).matcher(style);
        while (matcher.find()) {
            addIfPresent(urls, matcher.group(2));
        }
        return new ArrayList<>(urls);
    }

    private List<String> parseSrcset(String srcset) {
        if (!StringUtils.hasText(srcset)) {
            return List.of();
        }
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        for (String candidate : srcset.split(",")) {
            String trimmed = candidate.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            addIfPresent(urls, parts[0]);
        }
        return new ArrayList<>(urls);
    }

    private void addIfPresent(Collection<String> urls, @Nullable String candidate) {
        if (StringUtils.hasText(candidate)) {
            urls.add(candidate.trim());
        }
    }

    @Nullable
    private String normalizeContentType(String url, @Nullable String contentType, byte[] body) {
        if (StringUtils.hasText(contentType) && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return contentType;
        }

        String lowerUrl = url.toLowerCase(Locale.ROOT);
        String lowerBody = new String(body, java.nio.charset.StandardCharsets.UTF_8)
                .substring(0, Math.min(body.length, 256))
                .toLowerCase(Locale.ROOT);

        if (lowerUrl.endsWith(".svg") || lowerBody.contains("<svg")) {
            return "image/svg+xml";
        }
        if (lowerUrl.endsWith(".ico")) {
            return "image/x-icon";
        }
        if (lowerUrl.endsWith(".png")) {
            return "image/png";
        }
        if (lowerUrl.endsWith(".gif")) {
            return "image/gif";
        }
        if (lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerUrl.endsWith(".webp")) {
            return "image/webp";
        }
        return null;
    }

    String resolveUrl(String baseUrl, String rawUrl) {
        try {
            return URI.create(baseUrl).resolve(rawUrl).toString();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String resolveExtension(String sourceUrl, String contentType) {
        String lowerContentType = contentType.toLowerCase(Locale.ROOT);
        if (lowerContentType.contains("svg")) {
            return "svg";
        }
        if (lowerContentType.contains("png")) {
            return "png";
        }
        if (lowerContentType.contains("webp")) {
            return "webp";
        }
        if (lowerContentType.contains("gif")) {
            return "gif";
        }
        if (lowerContentType.contains("icon") || lowerContentType.contains("ico")) {
            return "ico";
        }

        String fileName = Paths.get(URI.create(sourceUrl).getPath()).getFileName() != null
                ? Paths.get(URI.create(sourceUrl).getPath()).getFileName().toString()
                : "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        }
        return "png";
    }

    private String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < 6; index++) {
                builder.append(String.format("%02x", hash[index]));
            }
            return builder.toString();
        } catch (Exception exception) {
            return UUID.randomUUID().toString().substring(0, 12);
        }
    }

    record PageExtraction(
            Document document,
            String pageUrl
    ) {
    }

    record LogoCandidate(
            String cacheKey,
            String sourceRef,
            String downloadUrl,
            StockBrandAssetSourceType sourceType,
            int priority,
            String referer,
            byte[] inlineBytes,
            String inlineContentType
    ) {
        private static LogoCandidate remote(
                String url,
                StockBrandAssetSourceType sourceType,
                int priority,
                @Nullable String referer
        ) {
            return new LogoCandidate(url, url, url, sourceType, priority, referer, null, null);
        }

        private static LogoCandidate inline(
                String sourceRef,
                byte[] inlineBytes,
                String inlineContentType,
                StockBrandAssetSourceType sourceType,
                int priority
        ) {
            return new LogoCandidate(sourceRef, sourceRef, null, sourceType, priority, null, inlineBytes, inlineContentType);
        }
    }

    record LogoBinary(
            byte[] bytes,
            String contentType
    ) {
    }

    record LogoCollectionResult(
            StockBrandAssetSourceType sourceType,
            String sourceUrl,
            String storagePath,
            String publicUrl,
            String contentType
    ) {
    }

    record DirectAssetOverride(
            String url,
            String referer,
            StockBrandAssetSourceType sourceType
    ) {
    }
}
