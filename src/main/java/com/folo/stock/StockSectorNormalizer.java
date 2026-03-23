package com.folo.stock;

import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StockSectorNormalizer {

    public static final String COMMUNICATION_SERVICES = "Communication Services";
    public static final String CONSUMER_DISCRETIONARY = "Consumer Discretionary";
    public static final String CONSUMER_STAPLES = "Consumer Staples";
    public static final String ENERGY = "Energy";
    public static final String FINANCIALS = "Financials";
    public static final String HEALTH_CARE = "Health Care";
    public static final String INDUSTRIALS = "Industrials";
    public static final String INFORMATION_TECHNOLOGY = "Information Technology";
    public static final String MATERIALS = "Materials";
    public static final String REAL_ESTATE = "Real Estate";
    public static final String UTILITIES = "Utilities";
    public static final String OTHER = "Other";

    private static final Map<String, String> EXACT_SECTOR_ALIASES = new LinkedHashMap<>();

    static {
        register(COMMUNICATION_SERVICES,
                "COMMUNICATION SERVICES", "COMMUNICATION", "COMMUNICATIONS", "MEDIA TELECOM",
                "MEDIA", "TELECOM", "TELECOMMUNICATIONS", "커뮤니케이션서비스",
                "커뮤니케이션 서비스", "통신", "미디어");
        register(CONSUMER_DISCRETIONARY,
                "CONSUMER DISCRETIONARY", "AUTOMOBILES", "AUTOMOBILE", "AUTO",
                "임의소비재", "자동차");
        register(CONSUMER_STAPLES,
                "CONSUMER STAPLES", "필수소비재");
        register(ENERGY,
                "ENERGY", "에너지");
        register(FINANCIALS,
                "FINANCIALS", "FINANCE", "FINANCIAL", "FINANCE INSURANCE AND REAL ESTATE",
                "FINANCIAL SERVICES", "금융", "은행", "보험", "증권");
        register(HEALTH_CARE,
                "HEALTH CARE", "HEALTHCARE", "HEALTH", "헬스케어", "의료");
        register(INDUSTRIALS,
                "INDUSTRIALS", "INDUSTRIAL", "MANUFACTURING", "CONSTRUCTION",
                "TRANSPORTATION", "SHIPBUILDING", "산업재", "제조", "건설", "운송", "조선");
        register(INFORMATION_TECHNOLOGY,
                "INFORMATION TECHNOLOGY", "TECHNOLOGY", "IT", "정보기술", "테크");
        register(MATERIALS,
                "MATERIALS", "MATERIAL", "ENERGY CHEMICALS", "CHEMICALS", "STEEL",
                "소재", "화학", "철강", "광업");
        register(REAL_ESTATE,
                "REAL ESTATE", "부동산");
        register(UTILITIES,
                "UTILITIES", "유틸리티", "전력");
        register(OTHER,
                "OTHER", "Venture", "VENTURE", "기타");
    }

    private static final List<KeywordRule> INDUSTRY_RULES = List.of(
            new KeywordRule(COMMUNICATION_SERVICES,
                    "SOCIAL", "MEDIA", "TELECOM", "TELECOMM", "BROADCAST", "ENTERTAINMENT",
                    "ADVERTIS", "COMMUNICATION", "INTERACTIVE"),
            new KeywordRule(INFORMATION_TECHNOLOGY,
                    "SEMICONDUCTOR", "SOFTWARE", "COMPUTER", "ELECTRONIC", "DATA PROCESS",
                    "INTERNET SERVICE", "INFORMATION RETRIEVAL", "CLOUD", "NETWORK",
                    "SAAS", "CYBER", "AI "),
            new KeywordRule(HEALTH_CARE,
                    "BIOTECH", "PHARM", "MEDICAL", "HEALTH", "THERAPEUTIC", "DRUG"),
            new KeywordRule(FINANCIALS,
                    "BANK", "INSURANCE", "SECURIT", "CAPITAL MARKET", "INVESTMENT",
                    "ASSET MANAGEMENT", "FINANC", "REIT", "BROKER"),
            new KeywordRule(REAL_ESTATE,
                    "REAL ESTATE", "PROPERTY", "REIT"),
            new KeywordRule(UTILITIES,
                    "UTILITY", "ELECTRIC POWER", "WATER SUPPLY", "GAS UTILITY"),
            new KeywordRule(ENERGY,
                    "OIL", "GAS", "ENERGY", "DRILL", "PIPELINE", "COAL", "REFIN"),
            new KeywordRule(MATERIALS,
                    "CHEMICAL", "STEEL", "METAL", "MINING", "PAPER", "FOREST",
                    "MATERIAL", "LUMBER"),
            new KeywordRule(CONSUMER_STAPLES,
                    "FOOD", "BEVERAGE", "GROCERY", "HOUSEHOLD", "TOBACCO", "AGRICULT",
                    "PERSONAL CARE"),
            new KeywordRule(CONSUMER_DISCRETIONARY,
                    "RETAIL", "APPAREL", "RESTAURANT", "LEISURE", "TRAVEL", "HOTEL",
                    "AUTO", "AUTOMOBILE"),
            new KeywordRule(INDUSTRIALS,
                    "AEROSPACE", "TRANSPORT", "AIRLINE", "MACHINERY", "CONSTRUCTION",
                    "SHIP", "ENGINEERING", "INDUSTRIAL", "LOGISTICS")
    );

    private StockSectorNormalizer() {
    }

    @Nullable
    public static String normalizeForMetadata(
            @Nullable String rawSector,
            @Nullable String rawIndustry,
            @Nullable StockClassificationScheme classificationScheme
    ) {
        String normalizedIndustry = normalizeFromIndustry(rawIndustry);
        if (normalizedIndustry != null) {
            return normalizedIndustry;
        }

        String normalizedSector = normalizeStoredSector(rawSector);
        if (normalizedSector != null) {
            return normalizedSector;
        }

        if (classificationScheme == StockClassificationScheme.SIC && StringUtils.hasText(rawSector)) {
            return normalizeFromSicDivision(rawSector);
        }

        return null;
    }

    @Nullable
    public static String normalizeStoredSector(@Nullable String rawSector) {
        if (!StringUtils.hasText(rawSector)) {
            return null;
        }

        String normalizedKey = normalizeKey(rawSector);
        String exact = EXACT_SECTOR_ALIASES.get(normalizedKey);
        if (exact != null) {
            return exact;
        }

        return normalizeFromSicDivision(rawSector);
    }

    @Nullable
    public static String normalizedSectorKey(@Nullable String rawSector) {
        String normalized = normalizeStoredSector(rawSector);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    @Nullable
    private static String normalizeFromIndustry(@Nullable String rawIndustry) {
        if (!StringUtils.hasText(rawIndustry)) {
            return null;
        }

        String normalized = normalizeKey(rawIndustry);
        for (KeywordRule rule : INDUSTRY_RULES) {
            if (rule.matches(normalized)) {
                return rule.sectorName();
            }
        }
        return null;
    }

    @Nullable
    private static String normalizeFromSicDivision(String rawSector) {
        String key = normalizeKey(rawSector);
        return switch (key) {
            case "AGRICULTURE FORESTRY AND FISHING" -> CONSUMER_STAPLES;
            case "MINING" -> MATERIALS;
            case "CONSTRUCTION", "MANUFACTURING", "WHOLESALE TRADE", "PUBLIC ADMINISTRATION" -> INDUSTRIALS;
            case "TRANSPORTATION COMMUNICATIONS AND UTILITIES" -> INDUSTRIALS;
            case "RETAIL TRADE" -> CONSUMER_DISCRETIONARY;
            case "SERVICES" -> COMMUNICATION_SERVICES;
            default -> null;
        };
    }

    private static void register(String canonicalSector, String... aliases) {
        for (String alias : aliases) {
            EXACT_SECTOR_ALIASES.put(normalizeKey(alias), canonicalSector);
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

    private record KeywordRule(
            String sectorName,
            List<String> keywords
    ) {
        private KeywordRule(String sectorName, String... keywords) {
            this(sectorName, List.of(keywords));
        }

        private boolean matches(String normalizedText) {
            return keywords.stream().anyMatch(normalizedText::contains);
        }
    }
}
