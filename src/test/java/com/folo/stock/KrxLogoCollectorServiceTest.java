package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import com.folo.config.FileStorageProperties;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class KrxLogoCollectorServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private StockBrandAssetRepository stockBrandAssetRepository;

    @Mock
    private StockIssuerProfileRepository stockIssuerProfileRepository;

    @Mock
    private StockSymbolRepository stockSymbolRepository;

    @Mock
    private StockSymbolSyncRunRepository stockSymbolSyncRunRepository;

    @Mock
    private StockEnrichmentTargetSelector stockEnrichmentTargetSelector;

    @Test
    void syncSymbolsCollectsAppleTouchIconFromHomepage() throws Exception {
        StockSymbol stockSymbol = stockSymbol(11L, "005930", "삼성전자");

        TestableKrxLogoCollectorService service = service();
        service.addPage("https://example.com/company", """
                <html>
                  <head>
                    <link rel="apple-touch-icon" href="/static/apple-touch-icon.png">
                  </head>
                  <body>company</body>
                </html>
                """);
        service.addRemoteBinary("https://example.com/static/apple-touch-icon.png", "image/png", "png-binary");

        KrxLogoCollectorService.PageExtraction pageExtraction = service.fetchPage("https://example.com/company");
        KrxLogoCollectorService.LogoCollectionResult result = service.collectFromPage(
                stockSymbol,
                pageExtraction.document(),
                pageExtraction.pageUrl()
        );

        assertThat(result).isNotNull();
        assertThat(result.sourceType()).isEqualTo(StockBrandAssetSourceType.APPLE_TOUCH_ICON);
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.publicUrl()).startsWith("/uploads/stock-logos/krx/005930-");
        assertThat(Files.exists(Path.of(result.storagePath()))).isTrue();
    }

    @Test
    void syncSymbolsExploresLinkedBrandPageAndStoresInlineSvgLogo() throws Exception {
        StockSymbol stockSymbol = stockSymbol(22L, "035420", "NAVER");
        StockIssuerProfile issuerProfile = issuerProfile(stockSymbol, "https://example.com/company", null);

        TestableKrxLogoCollectorService service = service();
        service.addPage("https://example.com/company", """
                <html>
                  <body>
                    <a href="/brand/ci">CI</a>
                  </body>
                </html>
                """);
        service.addPage("https://example.com/brand/ci", """
                <html>
                  <body>
                    <header>
                      <svg id="company-logo" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
                        <rect width="10" height="10" fill="#00c73c" />
                      </svg>
                    </header>
                  </body>
                </html>
                """);

        KrxLogoCollectorService.LogoCollectionResult result = service.collectLogo(stockSymbol, issuerProfile);

        assertThat(result).isNotNull();
        assertThat(result.sourceType()).isEqualTo(StockBrandAssetSourceType.LOGO_IMAGE);
        assertThat(result.contentType()).isEqualTo("image/svg+xml");
        assertThat(Files.readString(Path.of(result.storagePath()))).contains("<svg");
    }

    @Test
    void fetchPageFollowsJavascriptRedirect() {
        TestableKrxLogoCollectorService service = service();
        service.addPage("https://example.com", """
                <html><body><script>location.href='/brand/ci';</script></body></html>
                """);
        service.addPage("https://example.com/brand/ci", """
                <html><body><header><img src="/images/logo.png" alt="logo"></header></body></html>
                """);

        KrxLogoCollectorService.PageExtraction extraction = service.fetchPage("https://example.com");

        assertThat(extraction).isNotNull();
        assertThat(extraction.pageUrl()).isEqualTo("https://example.com/brand/ci");
    }

    @Test
    void fetchPageFollowsMetaRefreshRedirect() {
        TestableKrxLogoCollectorService service = service();
        service.addPage("https://example.com", """
                <html><head><meta http-equiv="refresh" content="0; url='/brand/ci'"></head></html>
                """);
        service.addPage("https://example.com/brand/ci", """
                <html><body><header><img src="/images/logo.png" alt="logo"></header></body></html>
                """);

        KrxLogoCollectorService.PageExtraction extraction = service.fetchPage("https://example.com");

        assertThat(extraction).isNotNull();
        assertThat(extraction.pageUrl()).isEqualTo("https://example.com/brand/ci");
    }

    @Test
    void collectLogoUsesKnownDirectAssetOverrideForRepresentativeTicker() {
        StockSymbol stockSymbol = stockSymbol(267250L, "267250", "HD현대");
        StockIssuerProfile issuerProfile = issuerProfile(stockSymbol, "https://www.hd.com", null);

        TestableKrxLogoCollectorService service = service();
        service.addRemoteBinary(
                "https://www.hd.com/common/kr/images/icon-brand-ci-logo.svg",
                "image/svg+xml",
                "<svg xmlns='http://www.w3.org/2000/svg'></svg>"
        );

        KrxLogoCollectorService.LogoCollectionResult result = service.collectLogo(stockSymbol, issuerProfile);

        assertThat(result).isNotNull();
        assertThat(result.sourceType()).isEqualTo(StockBrandAssetSourceType.LOGO_IMAGE);
        assertThat(result.contentType()).isEqualTo("image/svg+xml");
    }

    @Test
    void resolveCandidatesIncludesBackgroundImageLogo() {
        TestableKrxLogoCollectorService service = service();
        Document document = Jsoup.parse("""
                <html>
                  <body>
                    <header>
                      <div class="brand-logo" style="background-image:url('/assets/logo.svg')"></div>
                    </header>
                  </body>
                </html>
                """, "https://example.com");

        assertThat(service.resolveCandidates(document, "https://example.com")).anySatisfy(candidate -> {
            assertThat(candidate.downloadUrl()).isEqualTo("https://example.com/assets/logo.svg");
            assertThat(candidate.sourceType()).isEqualTo(StockBrandAssetSourceType.LOGO_IMAGE);
        });
    }

    private TestableKrxLogoCollectorService service() {
        return new TestableKrxLogoCollectorService(
                RestClient.builder(),
                new FileStorageProperties(tempDir.toString()),
                stockBrandAssetRepository,
                stockIssuerProfileRepository,
                stockSymbolRepository,
                stockSymbolSyncRunRepository,
                stockEnrichmentTargetSelector
        );
    }

    private StockSymbol stockSymbol(Long id, String ticker, String name) {
        StockSymbol stockSymbol = new StockSymbol();
        stockSymbol.setId(id);
        stockSymbol.setTicker(ticker);
        stockSymbol.setName(name);
        stockSymbol.setMarket(MarketType.KRX);
        stockSymbol.setAssetType(AssetType.STOCK);
        stockSymbol.setActive(true);
        stockSymbol.setPrimaryExchangeCode("XKRX");
        stockSymbol.setCurrencyCode("KRW");
        stockSymbol.setSourceProvider(StockDataProvider.KIS);
        stockSymbol.setSourceIdentifier(ticker);
        stockSymbol.setLastMasterSyncedAt(LocalDateTime.now());
        return stockSymbol;
    }

    private StockIssuerProfile issuerProfile(StockSymbol stockSymbol, String hmUrl, String irUrl) {
        StockIssuerProfile issuerProfile = new StockIssuerProfile();
        issuerProfile.setStockSymbol(stockSymbol);
        issuerProfile.setProvider(StockDataProvider.OPENDART);
        issuerProfile.setCorpCode("00123456");
        issuerProfile.setCorpName(stockSymbol.getName());
        issuerProfile.setStockCode(stockSymbol.getTicker());
        issuerProfile.setHmUrl(hmUrl);
        issuerProfile.setIrUrl(irUrl);
        issuerProfile.setIndutyCode("264");
        issuerProfile.setSourcePayloadVersion("opendart:v1/company");
        issuerProfile.setLastSyncedAt(LocalDateTime.now());
        return issuerProfile;
    }

    private static final class TestableKrxLogoCollectorService extends KrxLogoCollectorService {

        private final Map<String, String> pages = new HashMap<>();
        private final Map<String, KrxLogoCollectorService.LogoBinary> binaries = new HashMap<>();

        private TestableKrxLogoCollectorService(
                RestClient.Builder restClientBuilder,
                FileStorageProperties fileStorageProperties,
                StockBrandAssetRepository stockBrandAssetRepository,
                StockIssuerProfileRepository stockIssuerProfileRepository,
                StockSymbolRepository stockSymbolRepository,
                StockSymbolSyncRunRepository stockSymbolSyncRunRepository,
                StockEnrichmentTargetSelector stockEnrichmentTargetSelector
        ) {
            super(
                    restClientBuilder,
                    fileStorageProperties,
                    stockBrandAssetRepository,
                    stockIssuerProfileRepository,
                    stockSymbolRepository,
                    stockSymbolSyncRunRepository,
                    stockEnrichmentTargetSelector
            );
        }

        private void addPage(String pageUrl, String html) {
            pages.put(pageUrl, html);
        }

        private void addRemoteBinary(String url, String contentType, String body) {
            binaries.put(url, new KrxLogoCollectorService.LogoBinary(
                    body.getBytes(StandardCharsets.UTF_8),
                    contentType
            ));
        }

        @Override
        PageExtraction fetchPage(String pageUrl) {
            return fetchPage(pageUrl, 0);
        }

        private PageExtraction fetchPage(String pageUrl, int depth) {
            if (depth > 2) {
                return null;
            }
            String html = pages.get(pageUrl);
            if (html == null) {
                return null;
            }
            Document document = Jsoup.parse(html, pageUrl);
            String metaRefresh = document.select("meta[http-equiv=refresh]").stream()
                    .map(element -> element.attr("content"))
                    .filter(content -> content.contains("url="))
                    .map(content -> content.substring(content.indexOf("url=") + 4).replace("'", "").replace("\"", ""))
                    .findFirst()
                    .orElse(null);
            if (metaRefresh != null) {
                return fetchPage(document.baseUri().replaceAll("/?$", "") + (metaRefresh.startsWith("/") ? metaRefresh : "/" + metaRefresh), depth + 1);
            }

            String scriptRedirect = document.select("script").stream()
                    .map(element -> element.data().isBlank() ? element.html() : element.data())
                    .filter(script -> script.contains("location.href"))
                    .map(script -> script.substring(script.indexOf("='") + 2, script.lastIndexOf("'")))
                    .findFirst()
                    .orElse(null);
            if (scriptRedirect != null) {
                return fetchPage(document.baseUri().replaceAll("/?$", "") + (scriptRedirect.startsWith("/") ? scriptRedirect : "/" + scriptRedirect), depth + 1);
            }

            return new PageExtraction(document, pageUrl);
        }

        @Override
        LogoBinary materializeBinary(LogoCandidate candidate) {
            if (candidate.inlineBytes() != null && candidate.inlineContentType() != null) {
                return new LogoBinary(candidate.inlineBytes(), candidate.inlineContentType());
            }
            return binaries.get(candidate.sourceRef());
        }
    }
}
