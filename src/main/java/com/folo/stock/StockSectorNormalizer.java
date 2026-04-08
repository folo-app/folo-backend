package com.folo.stock;

import com.folo.common.enums.AssetType;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StockSectorNormalizer {

    public static final String COMMUNICATION_SERVICES = "Communication Services";
    public static final String CONGLOMERATES = "Conglomerates";
    public static final String CONSUMER_DISCRETIONARY = "Consumer Discretionary";
    public static final String CONSUMER_STAPLES = "Consumer Staples";
    public static final String ENERGY = "Energy";
    public static final String FINANCIALS = "Financials";
    public static final String HEALTH_CARE = "Health Care";
    public static final String HOLDING_COMPANIES = "Holding Companies";
    public static final String INDUSTRIALS = "Industrials";
    public static final String INFORMATION_TECHNOLOGY = "Information Technology";
    public static final String MATERIALS = "Materials";
    public static final String REAL_ESTATE = "Real Estate";
    public static final String UTILITIES = "Utilities";
    public static final String OTHER = "Other";

    private static final Map<StockSectorCode, String> LEGACY_CANONICAL_NAMES = new EnumMap<>(StockSectorCode.class);
    private static final Map<String, StockSectorCode> EXACT_SECTOR_ALIASES = new LinkedHashMap<>();

    static {
        LEGACY_CANONICAL_NAMES.put(StockSectorCode.COMMUNICATION_SERVICES, COMMUNICATION_SERVICES);
        LEGACY_CANONICAL_NAMES.put(StockSectorCode.CONGLOMERATES, CONGLOMERATES);
        LEGACY_CANONICAL_NAMES.put(StockSectorCode.CONSUMER_DISCRETIONARY, CONSUMER_DISCRETIONARY);
        LEGACY_CANONICAL_NAMES.put(StockSectorCode.CONSUMER_STAPLES, CONSUMER_STAPLES);
        LEGACY_CANONICAL_NAMES.put(StockSectorCode.ENERGY, ENERGY);
        LEGACY_CANONICAL_NAMES.put(StockSectorCode.FINANCIALS, FINANCIALS);
        LEGACY_CANONICAL_NAMES.put(StockSectorCode.HEALTH_CARE, HEALTH_CARE);
        LEGACY_CANONICAL_NAMES.put(StockSectorCode.HOLDING_COMPANIES, HOLDING_COMPANIES);
        LEGACY_CANONICAL_NAMES.put(StockSectorCode.INDUSTRIALS, INDUSTRIALS);
        LEGACY_CANONICAL_NAMES.put(StockSectorCode.INFORMATION_TECHNOLOGY, INFORMATION_TECHNOLOGY);
        LEGACY_CANONICAL_NAMES.put(StockSectorCode.MATERIALS, MATERIALS);
        LEGACY_CANONICAL_NAMES.put(StockSectorCode.REAL_ESTATE, REAL_ESTATE);
        LEGACY_CANONICAL_NAMES.put(StockSectorCode.UTILITIES, UTILITIES);
        LEGACY_CANONICAL_NAMES.put(StockSectorCode.OTHER, OTHER);

        register(StockSectorCode.COMMUNICATION_SERVICES,
                COMMUNICATION_SERVICES, "COMMUNICATION", "COMMUNICATIONS", "MEDIA TELECOM",
                "MEDIA", "TELECOM", "TELECOMMUNICATIONS", "커뮤니케이션서비스",
                "커뮤니케이션 서비스", "통신", "미디어");
        register(StockSectorCode.CONGLOMERATES,
                CONGLOMERATES, "CONGLOMERATE", "COMPOSITE BUSINESS", "복합기업");
        register(StockSectorCode.CONSUMER_DISCRETIONARY,
                CONSUMER_DISCRETIONARY, "AUTOMOBILES", "AUTOMOBILE", "AUTO",
                "임의소비재", "자동차", "경기소비재");
        register(StockSectorCode.CONSUMER_STAPLES,
                CONSUMER_STAPLES, "필수소비재");
        register(StockSectorCode.ENERGY,
                ENERGY, "에너지");
        register(StockSectorCode.FINANCIALS,
                FINANCIALS, "FINANCE", "FINANCIAL", "FINANCE INSURANCE AND REAL ESTATE",
                "FINANCIAL SERVICES", "금융", "은행", "보험", "증권");
        register(StockSectorCode.HEALTH_CARE,
                HEALTH_CARE, "HEALTHCARE", "HEALTH", "헬스케어", "의료");
        register(StockSectorCode.HOLDING_COMPANIES,
                HOLDING_COMPANIES, "HOLDING", "HOLDINGS", "HOLDCO", "지주", "지주사", "지주회사", "홀딩스");
        register(StockSectorCode.INDUSTRIALS,
                INDUSTRIALS, "INDUSTRIAL", "MANUFACTURING", "CONSTRUCTION",
                "TRANSPORTATION", "SHIPBUILDING", "산업재", "제조", "건설", "운송", "조선", "방산");
        register(StockSectorCode.INFORMATION_TECHNOLOGY,
                INFORMATION_TECHNOLOGY, "TECHNOLOGY", "IT", "정보기술", "테크");
        register(StockSectorCode.MATERIALS,
                MATERIALS, "MATERIAL", "ENERGY CHEMICALS", "CHEMICALS", "STEEL",
                "소재", "화학", "철강", "광업");
        register(StockSectorCode.REAL_ESTATE,
                REAL_ESTATE, "부동산");
        register(StockSectorCode.UTILITIES,
                UTILITIES, "유틸리티", "전력");
        register(StockSectorCode.OTHER,
                OTHER, "VENTURE", "기타");
    }

    private static final List<KeywordRule> INDUSTRY_RULES = List.of(
            new KeywordRule(StockSectorCode.INFORMATION_TECHNOLOGY,
                    "SEMICONDUCTOR", "SOFTWARE", "COMPUTER", "ELECTRONIC", "DATA PROCESS",
                    "INTERNET SERVICE", "INFORMATION RETRIEVAL", "CLOUD", "NETWORK",
                    "COMMUNICATIONS EQUIPMENT", "COMMUNICATION EQUIPMENT",
                    "SAAS", "CYBER", "AI ", "SMARTPHONE", "CONSUMER ELECTRONICS"),
            new KeywordRule(StockSectorCode.COMMUNICATION_SERVICES,
                    "SOCIAL", "MEDIA", "TELECOM", "TELECOMM", "BROADCAST", "ENTERTAINMENT",
                    "ADVERTIS", "COMMUNICATION", "INTERACTIVE"),
            new KeywordRule(StockSectorCode.HEALTH_CARE,
                    "BIOTECH", "PHARM", "MEDICAL", "HEALTH", "THERAPEUTIC", "DRUG"),
            new KeywordRule(StockSectorCode.HOLDING_COMPANIES,
                    "HOLDING", "HOLDINGS", "HOLDCO"),
            new KeywordRule(StockSectorCode.CONGLOMERATES,
                    "CONGLOMERATE", "MULTI INDUSTRY"),
            new KeywordRule(StockSectorCode.FINANCIALS,
                    "BANK", "INSURANCE", "SECURIT", "CAPITAL MARKET", "INVESTMENT",
                    "ASSET MANAGEMENT", "FINANC", "REIT", "BROKER"),
            new KeywordRule(StockSectorCode.REAL_ESTATE,
                    "REAL ESTATE", "PROPERTY", "REIT"),
            new KeywordRule(StockSectorCode.UTILITIES,
                    "UTILITY", "ELECTRIC POWER", "WATER SUPPLY", "GAS UTILITY"),
            new KeywordRule(StockSectorCode.ENERGY,
                    "OIL", "GAS", "ENERGY", "DRILL", "PIPELINE", "COAL", "REFIN"),
            new KeywordRule(StockSectorCode.MATERIALS,
                    "CHEMICAL", "STEEL", "METAL", "MINING", "PAPER", "FOREST",
                    "MATERIAL", "LUMBER"),
            new KeywordRule(StockSectorCode.CONSUMER_STAPLES,
                    "FOOD", "BEVERAGE", "GROCERY", "HOUSEHOLD", "TOBACCO", "AGRICULT",
                    "PERSONAL CARE"),
            new KeywordRule(StockSectorCode.CONSUMER_DISCRETIONARY,
                    "RETAIL", "APPAREL", "RESTAURANT", "LEISURE", "TRAVEL", "HOTEL",
                    "AUTO", "AUTOMOBILE", "E-COMMERCE", "E COMMERCE"),
            new KeywordRule(StockSectorCode.INDUSTRIALS,
                    "AEROSPACE", "DEFENSE", "MILITARY", "TRANSPORT", "AIRLINE", "MACHINERY",
                    "CONSTRUCTION", "SHIP", "ENGINEERING", "INDUSTRIAL", "LOGISTICS")
    );

    private StockSectorNormalizer() {
    }

    public static ResolvedSector resolve(
            AssetType assetType,
            @Nullable StockSectorCode storedSectorCode,
            @Nullable String storedSectorName,
            @Nullable String rawSector,
            @Nullable String rawIndustry,
            @Nullable StockClassificationScheme classificationScheme
    ) {
        if (assetType == AssetType.ETF) {
            return ResolvedSector.etf();
        }

        StockSectorCode metadataCode = normalizeSectorCodeForMetadata(rawSector, rawIndustry, classificationScheme);
        if (metadataCode != null && metadataCode != StockSectorCode.OTHER) {
            return ResolvedSector.of(metadataCode);
        }

        if (storedSectorCode != null && storedSectorCode != StockSectorCode.OTHER) {
            return ResolvedSector.of(storedSectorCode);
        }

        StockSectorCode normalizedStoredCode = normalizeStoredSectorCode(storedSectorName);
        if (normalizedStoredCode != null && normalizedStoredCode != StockSectorCode.OTHER) {
            return ResolvedSector.of(normalizedStoredCode);
        }

        if (metadataCode != null) {
            return ResolvedSector.of(metadataCode);
        }

        if (storedSectorCode == StockSectorCode.OTHER || normalizedStoredCode == StockSectorCode.OTHER) {
            return ResolvedSector.of(StockSectorCode.OTHER);
        }

        return ResolvedSector.of(StockSectorCode.OTHER);
    }

    @Nullable
    public static StockSectorCode normalizeSectorCodeForMetadata(
            @Nullable String rawSector,
            @Nullable String rawIndustry,
            @Nullable StockClassificationScheme classificationScheme
    ) {
        StockSectorCode normalizedIndustry = normalizeFromIndustry(rawIndustry);
        if (normalizedIndustry != null) {
            return normalizedIndustry;
        }

        StockSectorCode normalizedSector = normalizeStoredSectorCode(rawSector);
        if (normalizedSector != null) {
            return normalizedSector;
        }

        if (classificationScheme == StockClassificationScheme.SIC && StringUtils.hasText(rawSector)) {
            return normalizeFromSicDivision(rawSector);
        }

        return null;
    }

    @Nullable
    public static StockSectorCode normalizeStoredSectorCode(@Nullable String rawSector) {
        if (!StringUtils.hasText(rawSector)) {
            return null;
        }

        String normalizedKey = normalizeKey(rawSector);
        StockSectorCode exact = EXACT_SECTOR_ALIASES.get(normalizedKey);
        if (exact != null) {
            return exact;
        }

        if (normalizedKey.startsWith("GICS ")) {
            return EXACT_SECTOR_ALIASES.get(normalizedKey.substring(5));
        }

        return normalizeFromSicDivision(rawSector);
    }

    public static String displayLabel(StockSectorCode sectorCode) {
        return sectorCode.label();
    }

    public static String canonicalName(StockSectorCode sectorCode) {
        return LEGACY_CANONICAL_NAMES.get(sectorCode);
    }

    @Nullable
    public static String normalizeForMetadata(
            @Nullable String rawSector,
            @Nullable String rawIndustry,
            @Nullable StockClassificationScheme classificationScheme
    ) {
        StockSectorCode code = normalizeSectorCodeForMetadata(rawSector, rawIndustry, classificationScheme);
        return code == null ? null : canonicalName(code);
    }

    @Nullable
    public static String normalizeStoredSector(@Nullable String rawSector) {
        StockSectorCode code = normalizeStoredSectorCode(rawSector);
        return code == null ? null : canonicalName(code);
    }

    @Nullable
    public static String normalizedSectorKey(@Nullable String rawSector) {
        StockSectorCode code = normalizeStoredSectorCode(rawSector);
        return code == null ? null : code.name();
    }

    @Nullable
    private static StockSectorCode normalizeFromIndustry(@Nullable String rawIndustry) {
        if (!StringUtils.hasText(rawIndustry)) {
            return null;
        }

        String normalized = normalizeKey(rawIndustry);
        for (KeywordRule rule : INDUSTRY_RULES) {
            if (rule.matches(normalized)) {
                return rule.sectorCode();
            }
        }
        return null;
    }

    @Nullable
    private static StockSectorCode normalizeFromSicDivision(String rawSector) {
        String key = normalizeKey(rawSector);
        return switch (key) {
            case "AGRICULTURE FORESTRY AND FISHING" -> StockSectorCode.CONSUMER_STAPLES;
            case "MINING" -> StockSectorCode.MATERIALS;
            case "CONSTRUCTION", "MANUFACTURING", "WHOLESALE TRADE", "PUBLIC ADMINISTRATION" ->
                    StockSectorCode.INDUSTRIALS;
            case "TRANSPORTATION COMMUNICATIONS AND UTILITIES" -> StockSectorCode.INDUSTRIALS;
            case "RETAIL TRADE" -> StockSectorCode.CONSUMER_DISCRETIONARY;
            case "SERVICES" -> StockSectorCode.COMMUNICATION_SERVICES;
            default -> null;
        };
    }

    private static void register(StockSectorCode sectorCode, String... aliases) {
        EXACT_SECTOR_ALIASES.put(normalizeKey(sectorCode.name()), sectorCode);
        EXACT_SECTOR_ALIASES.put(normalizeKey(sectorCode.label()), sectorCode);
        EXACT_SECTOR_ALIASES.put(normalizeKey(canonicalName(sectorCode)), sectorCode);
        for (String alias : aliases) {
            EXACT_SECTOR_ALIASES.put(normalizeKey(alias), sectorCode);
        }
    }

    private static String normalizeKey(String raw) {
        return raw.trim()
                .toUpperCase(Locale.ROOT)
                .replace('&', ' ')
                .replace('/', ' ')
                .replaceAll("[^\\p{L}\\p{Nd}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public record ResolvedSector(
            String key,
            String label,
            @Nullable StockSectorCode code
    ) {
        private static ResolvedSector of(StockSectorCode code) {
            return new ResolvedSector(code.key(), code.label(), code);
        }

        private static ResolvedSector etf() {
            return new ResolvedSector("etf", "ETF", null);
        }
    }

    private record KeywordRule(
            StockSectorCode sectorCode,
            List<String> keywords
    ) {
        private KeywordRule(StockSectorCode sectorCode, String... keywords) {
            this(sectorCode, List.of(keywords));
        }

        private boolean matches(String normalizedText) {
            return keywords.stream().anyMatch(normalizedText::contains);
        }
    }
}
