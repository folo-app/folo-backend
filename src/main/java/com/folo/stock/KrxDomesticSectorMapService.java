package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class KrxDomesticSectorMapService {

    private static final String DEFAULT_SOURCE_PAYLOAD_VERSION = "krx:sector-map:v1";
    private static final String HEURISTIC_SOURCE_PAYLOAD_VERSION = "krx:sector-heuristics:v1";
    private static final String PREFERRED_INHERITANCE_SOURCE_PAYLOAD_VERSION = "krx:preferred-share-inheritance:v1";
    private static final String HEALTH_CARE = "Health Care";
    private static final String INFORMATION_TECHNOLOGY = "Information Technology";
    private static final String COMMUNICATION_SERVICES = "Communication Services";
    private static final String CONGLOMERATES = "Conglomerates";
    private static final String CONSUMER_STAPLES = "Consumer Staples";
    private static final String CONSUMER_DISCRETIONARY = "Consumer Discretionary";
    private static final String FINANCIALS = "Financials";
    private static final String INDUSTRIALS = "Industrials";
    private static final String HOLDING_COMPANIES = "Holding Companies";
    private static final String MATERIALS = "Materials";
    private static final String ENERGY = "Energy";
    private static final String REAL_ESTATE = "Real Estate";
    private static final String UTILITIES = "Utilities";

    private static final java.util.List<NameHeuristicRule> NAME_HEURISTIC_RULES = java.util.List.of(
            new NameHeuristicRule(REAL_ESTATE, "REITs",
                    "리츠", "REIT", "REITS"),
            new NameHeuristicRule(FINANCIALS, "Insurance",
                    "보험", "손해보험", "화재"),
            new NameHeuristicRule(FINANCIALS, "Capital Markets",
                    "증권", "인베스트", "벤처스", "벤처투자", "창투", "파트너스",
                    "기술투자", "IB투자"),
            new NameHeuristicRule(FINANCIALS, "Banks",
                    "은행"),
            new NameHeuristicRule(FINANCIALS, "Financial Services",
                    "금융", "캐피탈", "파이낸셜", "카드"),
            new NameHeuristicRule(HOLDING_COMPANIES, "Holding Companies",
                    "홀딩스", "지주", "HOLDINGS", "HOLDING"),
            new NameHeuristicRule(CONGLOMERATES, "Conglomerates",
                    "복합기업"),
            new NameHeuristicRule(HEALTH_CARE, "Pharmaceuticals",
                    "제약", "약품"),
            new NameHeuristicRule(HEALTH_CARE, "Biotechnology",
                    "바이오", "헬스", "헬스케어", "메디", "의료", "테라퓨틱", "파마"),
            new NameHeuristicRule(HEALTH_CARE, "Health Care Equipment",
                    "사이언스", "진단"),
            new NameHeuristicRule(INFORMATION_TECHNOLOGY, "Semiconductors",
                    "반도체", "세미콘", "디스플레이"),
            new NameHeuristicRule(INFORMATION_TECHNOLOGY, "Technology Hardware",
                    "전자", "이노텍", "아이티", "IT"),
            new NameHeuristicRule(INFORMATION_TECHNOLOGY, "Software",
                    "소프트", "테크", "시스템", "플랫폼"),
            new NameHeuristicRule(COMMUNICATION_SERVICES, "Interactive Media & Services",
                    "NAVER", "KAKAO", "NHN", "SOOP", "포털", "통신", "콘텐츠", "미디어"),
            new NameHeuristicRule(COMMUNICATION_SERVICES, "Entertainment",
                    "엔터", "ENT", "뮤직", "CGV", "서재", "게임"),
            new NameHeuristicRule(CONSUMER_STAPLES, "Food Products",
                    "식품", "푸드", "제당", "제분", "삼립", "씨푸드"),
            new NameHeuristicRule(CONSUMER_STAPLES, "Beverages",
                    "주류", "음료", "맥주", "진로", "에탄올"),
            new NameHeuristicRule(CONSUMER_STAPLES, "Personal Care Products",
                    "화장품", "코스믹", "생활건강", "생활용품"),
            new NameHeuristicRule(CONSUMER_DISCRETIONARY, "Automobiles",
                    "자동차", "모터스", "모빌리티"),
            new NameHeuristicRule(CONSUMER_DISCRETIONARY, "Automobile Components",
                    "오토", "모비스", "타이어"),
            new NameHeuristicRule(CONSUMER_DISCRETIONARY, "Broadline Retail",
                    "리테일", "백화점", "쇼핑", "커머스", "면세", "마트"),
            new NameHeuristicRule(CONSUMER_DISCRETIONARY, "Textiles, Apparel & Luxury Goods",
                    "의류", "패션", "방직", "섬유", "동일"),
            new NameHeuristicRule(CONSUMER_DISCRETIONARY, "Hotels, Restaurants & Leisure",
                    "레저", "여행", "호텔"),
            new NameHeuristicRule(MATERIALS, "Chemicals",
                    "화학", "케미칼", "도료", "페인트", "소재", "머트리얼"),
            new NameHeuristicRule(MATERIALS, "Metals & Mining",
                    "철강", "스틸", "메탈", "제강", "제철", "금속", "제련", "비철"),
            new NameHeuristicRule(MATERIALS, "Paper & Forest Products",
                    "제지", "펄프", "목재", "제분"),
            new NameHeuristicRule(MATERIALS, "Construction Materials",
                    "시멘트", "레미콘", "세라믹", "유리", "글라스"),
            new NameHeuristicRule(INDUSTRIALS, "Construction & Engineering",
                    "건설", "토건", "엔지니어링", "플랜트", "산업개발"),
            new NameHeuristicRule(INDUSTRIALS, "Electrical Equipment",
                    "전선", "일렉트릭", "전기"),
            new NameHeuristicRule(INDUSTRIALS, "Machinery",
                    "기계", "기공", "엔진"),
            new NameHeuristicRule(INDUSTRIALS, "Transportation",
                    "물류", "로지스", "해운", "운수", "운송", "항공", "택배"),
            new NameHeuristicRule(INDUSTRIALS, "Trading Companies & Distributors",
                    "인터내셔널", "글로벌", "상사", "네트웍스", "네트워크"),
            new NameHeuristicRule(INDUSTRIALS, "Aerospace & Defense",
                    "방산", "넥스원"),
            new NameHeuristicRule(INDUSTRIALS, "Building Products",
                    "주철관"),
            new NameHeuristicRule(ENERGY, "Oil, Gas & Consumable Fuels",
                    "석유", "오일", "가스", "정유", "에너비스"),
            new NameHeuristicRule(UTILITIES, "Electric Utilities",
                    "전력", "지역난방", "수도", "상수도")
    );

    private final String source;
    private final KrxPreferredStockResolver krxPreferredStockResolver;
    private final StockIssuerProfileRepository stockIssuerProfileRepository;
    private volatile CachedSectorMap cachedSectorMap;

    public KrxDomesticSectorMapService(
            @Value("${integration.market-data.kis.domestic-sector-map-file-url:data/kis/kis-domestic-sector-map.csv}")
            String source,
            KrxPreferredStockResolver krxPreferredStockResolver,
            StockIssuerProfileRepository stockIssuerProfileRepository
    ) {
        this.source = source;
        this.krxPreferredStockResolver = krxPreferredStockResolver;
        this.stockIssuerProfileRepository = stockIssuerProfileRepository;
    }

    public boolean isConfigured() {
        if (!StringUtils.hasText(source)) {
            return false;
        }

        return isRemoteSource(source) || Files.exists(Path.of(source));
    }

    @Nullable
    public StockMetadataEnrichmentRecord resolve(StockSymbol stockSymbol) {
        if (!isEligible(stockSymbol)) {
            return null;
        }

        SectorMapRow explicitTickerRow = resolveTickerRow(stockSymbol);
        if (explicitTickerRow != null) {
            return toRecord(explicitTickerRow);
        }

        if (stockSymbol.getSectorCode() != null && stockSymbol.getSectorCode() != StockSectorCode.OTHER) {
            return null;
        }

        if (StockSectorNormalizer.normalizeStoredSectorCode(stockSymbol.getSectorName()) != null
                && StockSectorNormalizer.normalizeStoredSectorCode(stockSymbol.getSectorName()) != StockSectorCode.OTHER) {
            return null;
        }

        StockMetadataEnrichmentRecord inheritedRecord = resolveByPreferredInheritance(stockSymbol);
        if (inheritedRecord != null) {
            return inheritedRecord;
        }

        StockIssuerProfile issuerProfile = resolveIssuerProfile(stockSymbol);
        return resolveLocalMappingOrHeuristics(stockSymbol, issuerProfile);
    }

    private SectorMapRow resolveByIndustryCode(@Nullable StockIssuerProfile issuerProfile, Map<String, SectorMapRow> rowsByIndustryCode) {
        if (issuerProfile == null || rowsByIndustryCode.isEmpty()) {
            return null;
        }

        String indutyCode = issuerProfile.getIndutyCode();
        if (!StringUtils.hasText(indutyCode)) {
            return null;
        }
        return rowsByIndustryCode.get(indutyCode.trim());
    }

    @Nullable
    private StockMetadataEnrichmentRecord resolveByNameHeuristics(
            StockSymbol stockSymbol,
            @Nullable StockIssuerProfile issuerProfile
    ) {
        String normalizedName = normalizeName(stockSymbol.getName());
        if (!StringUtils.hasText(normalizedName)) {
            return null;
        }

        for (NameHeuristicRule rule : NAME_HEURISTIC_RULES) {
            if (rule.matches(normalizedName)) {
                return new StockMetadataEnrichmentRecord(
                        rule.sectorName(),
                        rule.industryName(),
                        StockClassificationScheme.KRX_SECTOR_MAP,
                        HEURISTIC_SOURCE_PAYLOAD_VERSION
                );
            }
        }

        if (issuerProfile != null && StringUtils.hasText(issuerProfile.getIndutyCode())) {
            return heuristicFromIndustryCode(issuerProfile.getIndutyCode());
        }

        return null;
    }

    @Nullable
    private StockMetadataEnrichmentRecord resolveByPreferredInheritance(StockSymbol stockSymbol) {
        StockSymbol baseCommonStock = krxPreferredStockResolver.resolveBaseCommonStock(stockSymbol);
        if (baseCommonStock == null) {
            return null;
        }

        StockSectorNormalizer.ResolvedSector inheritedSector = StockSectorNormalizer.resolve(
                baseCommonStock.getAssetType(),
                baseCommonStock.getSectorCode(),
                baseCommonStock.getSectorName(),
                null,
                null,
                null
        );
        if (inheritedSector.code() != null && inheritedSector.code() != StockSectorCode.OTHER) {
            return inheritedRecord(inheritedSector.code());
        }

        StockIssuerProfile issuerProfile = resolveIssuerProfile(baseCommonStock);
        StockMetadataEnrichmentRecord commonStockRecord = resolveLocalMappingOrHeuristics(baseCommonStock, issuerProfile);
        if (commonStockRecord == null) {
            return null;
        }

        StockSectorCode inheritedCode = StockSectorNormalizer.normalizeSectorCodeForMetadata(
                commonStockRecord.sectorNameRaw(),
                commonStockRecord.industryNameRaw(),
                commonStockRecord.classificationScheme()
        );
        return inheritedCode == null || inheritedCode == StockSectorCode.OTHER
                ? null
                : inheritedRecord(inheritedCode);
    }

    @Nullable
    private StockMetadataEnrichmentRecord resolveLocalMappingOrHeuristics(
            StockSymbol stockSymbol,
            @Nullable StockIssuerProfile issuerProfile
    ) {
        if (isConfigured()) {
            CachedSectorMap sectorMap = loadSectorMap();
            SectorMapRow row = sectorMap.rowsByTicker().get(stockSymbol.getTicker());
            if (row == null) {
                row = resolveByIndustryCode(issuerProfile, sectorMap.rowsByIndutyCode());
            }
            if (row != null) {
                return toRecord(row);
            }
        }

        return resolveByNameHeuristics(stockSymbol, issuerProfile);
    }

    @Nullable
    private SectorMapRow resolveTickerRow(StockSymbol stockSymbol) {
        if (!isConfigured()) {
            return null;
        }
        return loadSectorMap().rowsByTicker().get(stockSymbol.getTicker());
    }

    @Nullable
    private StockMetadataEnrichmentRecord heuristicFromIndustryCode(String indutyCode) {
        String normalizedCode = indutyCode.trim();
        if (normalizedCode.startsWith("212")) {
            return heuristicRecord(HEALTH_CARE, "Pharmaceuticals");
        }
        if (normalizedCode.startsWith("211") || normalizedCode.startsWith("213") || normalizedCode.startsWith("701")) {
            return heuristicRecord(HEALTH_CARE, "Biotechnology");
        }
        if (normalizedCode.startsWith("261") || normalizedCode.startsWith("264")) {
            return heuristicRecord(INFORMATION_TECHNOLOGY, "Semiconductors");
        }
        if (normalizedCode.startsWith("631")) {
            return heuristicRecord(COMMUNICATION_SERVICES, "Interactive Media & Services");
        }
        if (normalizedCode.startsWith("651")) {
            return heuristicRecord(FINANCIALS, "Insurance");
        }
        if (normalizedCode.startsWith("661")) {
            return heuristicRecord(FINANCIALS, "Capital Markets");
        }
        if (normalizedCode.startsWith("301") || normalizedCode.startsWith("303") || normalizedCode.startsWith("319")) {
            return heuristicRecord(CONSUMER_DISCRETIONARY, "Automobiles");
        }
        if (normalizedCode.startsWith("102") || normalizedCode.startsWith("105") || normalizedCode.startsWith("111")) {
            return heuristicRecord(CONSUMER_STAPLES, "Food Products");
        }
        if (normalizedCode.startsWith("201")
                || normalizedCode.startsWith("204")
                || normalizedCode.startsWith("232")
                || normalizedCode.startsWith("233")
                || normalizedCode.startsWith("241")
                || normalizedCode.startsWith("242")
                || normalizedCode.startsWith("259")) {
            return heuristicRecord(MATERIALS, "Materials");
        }
        if (normalizedCode.startsWith("281")
                || normalizedCode.startsWith("283")
                || normalizedCode.startsWith("284")
                || normalizedCode.startsWith("289")
                || normalizedCode.startsWith("292")
                || normalizedCode.startsWith("412")
                || normalizedCode.startsWith("461")
                || normalizedCode.startsWith("468")
                || normalizedCode.startsWith("492")
                || normalizedCode.startsWith("715")) {
            return heuristicRecord(INDUSTRIALS, "Industrials");
        }
        if (normalizedCode.startsWith("467")) {
            return heuristicRecord(ENERGY, "Oil, Gas & Consumable Fuels");
        }
        if (normalizedCode.startsWith("471")) {
            return heuristicRecord(CONSUMER_DISCRETIONARY, "Broadline Retail");
        }
        if (normalizedCode.startsWith("171")) {
            return heuristicRecord(MATERIALS, "Paper & Forest Products");
        }
        if (normalizedCode.startsWith("141") || normalizedCode.startsWith("131") || normalizedCode.equals("132") || normalizedCode.startsWith("681")) {
            return heuristicRecord(CONSUMER_DISCRETIONARY, "Textiles, Apparel & Luxury Goods");
        }
        return null;
    }

    private StockMetadataEnrichmentRecord heuristicRecord(String sectorName, String industryName) {
        return new StockMetadataEnrichmentRecord(
                sectorName,
                industryName,
                StockClassificationScheme.KRX_SECTOR_MAP,
                HEURISTIC_SOURCE_PAYLOAD_VERSION
        );
    }

    private StockMetadataEnrichmentRecord inheritedRecord(StockSectorCode sectorCode) {
        return new StockMetadataEnrichmentRecord(
                StockSectorNormalizer.canonicalName(sectorCode),
                null,
                StockClassificationScheme.KRX_SECTOR_MAP,
                PREFERRED_INHERITANCE_SOURCE_PAYLOAD_VERSION
        );
    }

    @Nullable
    private StockIssuerProfile resolveIssuerProfile(StockSymbol stockSymbol) {
        if (stockSymbol.getId() == null) {
            return null;
        }

        return stockIssuerProfileRepository.findByStockSymbolIdAndProvider(stockSymbol.getId(), StockDataProvider.OPENDART)
                .orElse(null);
    }

    private StockMetadataEnrichmentRecord toRecord(SectorMapRow row) {
        return new StockMetadataEnrichmentRecord(
                row.sectorName(),
                row.industryName(),
                StockClassificationScheme.KRX_SECTOR_MAP,
                row.sourcePayloadVersion()
        );
    }

    private boolean isEligible(StockSymbol stockSymbol) {
        return stockSymbol.getMarket() == MarketType.KRX
                && stockSymbol.getAssetType() == AssetType.STOCK
                && stockSymbol.isActive();
    }

    private CachedSectorMap loadSectorMap() {
        FileTime lastModified = resolveLastModified(source);
        CachedSectorMap cached = cachedSectorMap;
        if (cached != null && cached.source().equals(source) && sameLastModified(cached.lastModified(), lastModified)) {
            return cached;
        }

        synchronized (this) {
            cached = cachedSectorMap;
            if (cached != null && cached.source().equals(source) && sameLastModified(cached.lastModified(), lastModified)) {
                return cached;
            }

            CachedSectorMap loaded = loadFromSource(source, lastModified);
            cachedSectorMap = loaded;
            return loaded;
        }
    }

    private CachedSectorMap loadFromSource(String source, FileTime lastModified) {
        Map<String, SectorMapRow> rowsByTicker = new LinkedHashMap<>();
        Map<String, SectorMapRow> rowsByIndutyCode = new LinkedHashMap<>();

        try (Reader reader = openReader(source)) {
            Iterable<CSVRecord> csvRecords = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .build()
                    .parse(reader);

            for (CSVRecord record : csvRecords) {
                SectorMapRow row = toRow(record);
                if (row == null) {
                    continue;
                }

                if (StringUtils.hasText(row.ticker())) {
                    rowsByTicker.put(row.ticker(), row);
                }
                if (StringUtils.hasText(row.indutyCode())) {
                    rowsByIndutyCode.put(row.indutyCode(), row);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("KRX domestic sector map load failed", exception);
        }

        return new CachedSectorMap(source, lastModified, rowsByTicker, rowsByIndutyCode);
    }

    @Nullable
    private SectorMapRow toRow(CSVRecord record) {
        String ticker = normalize(firstPresent(record, "ticker", "stockCode"));
        String indutyCode = normalize(firstPresent(record, "indutyCode", "industryCode"));
        String sectorName = normalize(firstPresent(record, "sectorName", "sector_name"));
        String industryName = normalize(firstPresent(record, "industryName", "industry_name"));
        String sourcePayloadVersion = normalize(firstPresent(record, "sourcePayloadVersion", "source_payload_version"));

        if (!StringUtils.hasText(ticker) && !StringUtils.hasText(indutyCode)) {
            return null;
        }
        if (!StringUtils.hasText(sectorName) && !StringUtils.hasText(industryName)) {
            return null;
        }

        return new SectorMapRow(
                ticker != null ? ticker.toUpperCase() : null,
                indutyCode,
                sectorName,
                industryName,
                sourcePayloadVersion == null ? DEFAULT_SOURCE_PAYLOAD_VERSION : sourcePayloadVersion
        );
    }

    private Reader openReader(String source) throws IOException {
        if (isRemoteSource(source)) {
            return new InputStreamReader(URI.create(source).toURL().openStream(), StandardCharsets.UTF_8);
        }

        return Files.newBufferedReader(Path.of(source), StandardCharsets.UTF_8);
    }

    private FileTime resolveLastModified(String source) {
        if (isRemoteSource(source)) {
            return null;
        }

        try {
            return Files.getLastModifiedTime(Path.of(source));
        } catch (IOException exception) {
            return null;
        }
    }

    private boolean sameLastModified(FileTime left, FileTime right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.equals(right);
    }

    private boolean isRemoteSource(String source) {
        return source.startsWith("http://") || source.startsWith("https://");
    }

    @Nullable
    private String firstPresent(CSVRecord record, String... columnNames) {
        for (String columnName : columnNames) {
            if (!record.isMapped(columnName)) {
                continue;
            }

            String value = record.get(columnName);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    @Nullable
    private String normalize(@Nullable String raw) {
        return StringUtils.hasText(raw) ? raw.trim() : null;
    }

    private String normalizeName(@Nullable String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }

        return raw.trim()
                .toUpperCase(java.util.Locale.ROOT)
                .replace(" ", "")
                .replace("&", "")
                .replace(".", "")
                .replace("-", "");
    }

    private record CachedSectorMap(
            String source,
            FileTime lastModified,
            Map<String, SectorMapRow> rowsByTicker,
            Map<String, SectorMapRow> rowsByIndutyCode
    ) {
    }

    private record SectorMapRow(
            @Nullable String ticker,
            @Nullable String indutyCode,
            @Nullable String sectorName,
            @Nullable String industryName,
            String sourcePayloadVersion
    ) {
    }

    private record NameHeuristicRule(
            String sectorName,
            String industryName,
            String... keywords
    ) {
        private boolean matches(String normalizedName) {
            for (String keyword : keywords) {
                String normalizedKeyword = keyword.trim()
                        .toUpperCase(java.util.Locale.ROOT)
                        .replace(" ", "")
                        .replace("&", "")
                        .replace(".", "")
                        .replace("-", "");
                if (normalizedName.contains(normalizedKeyword)) {
                    return true;
                }
            }
            return false;
        }
    }
}
