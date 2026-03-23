package com.folo.stock;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Component
public class KrxBrandFamilyResolver {

    private static final List<String> PREFERRED_SUFFIXES = List.of(
            "4우(전환)",
            "3우(전환)",
            "2우(전환)",
            "1우(전환)",
            "우(전환)",
            "3우B",
            "2우B",
            "1우B",
            "우B",
            "4우",
            "3우",
            "2우",
            "1우",
            "우"
    );

    private static final List<KrxBrandFamily> FAMILY_RULES = List.of(
            KrxBrandFamily.prefix("LG", List.of("003550"), List.of("LG", "엘지")),
            KrxBrandFamily.prefix("SK", List.of("034730"), List.of("SK", "에스케이")),
            KrxBrandFamily.prefix("SAMSUNG", List.of("005930", "028260"), List.of("삼성", "SAMSUNG")),
            KrxBrandFamily.prefix("CJ", List.of("001040"), List.of("CJ")),
            KrxBrandFamily.prefix("LOTTE", List.of("004990"), List.of("롯데", "LOTTE")),
            KrxBrandFamily.prefix("HANWHA", List.of("000880"), List.of("한화", "HANWHA")),
            KrxBrandFamily.prefix("GS", List.of("078930"), List.of("GS")),
            KrxBrandFamily.prefix("DOOSAN", List.of("000150"), List.of("두산", "DOOSAN")),
            KrxBrandFamily.prefix("DB", List.of("012030", "000990"), List.of("DB", "디비")),
            KrxBrandFamily.prefix("ECOPRO", List.of("086520"), List.of("에코프로", "ECOPRO")),
            KrxBrandFamily.prefix("HD_HYUNDAI", List.of("267250"), List.of("HD현대")),
            KrxBrandFamily.prefix("HYOSUNG", List.of("004800"), List.of("효성", "HYOSUNG")),
            KrxBrandFamily.prefix("KAKAO", List.of("035720"), List.of("카카오", "KAKAO")),
            KrxBrandFamily.prefix("SHINSEGAE", List.of("004170"), List.of("신세계", "SHINSEGAE")),
            KrxBrandFamily.prefix("KOLON", List.of("002020"), List.of("코오롱", "KOLON")),
            KrxBrandFamily.prefix("AMOREPACIFIC", List.of("002790"), List.of("아모레", "AMORE")),
            KrxBrandFamily.prefix("CELLTRION", List.of("068270"), List.of("셀트리온", "CELLTRION")),
            KrxBrandFamily.prefix("OCI", List.of("010060"), List.of("OCI")),
            KrxBrandFamily.prefix("POSCO", List.of("005490"), List.of("POSCO", "포스코")),
            KrxBrandFamily.prefix("HANJIN", List.of("180640"), List.of("한진", "HANJIN")),
            KrxBrandFamily.prefix("HLB", List.of("028300"), List.of("HLB")),
            KrxBrandFamily.withExclusions("HL", List.of("060980"), List.of("HL"), List.of("HLB")),
            KrxBrandFamily.prefix(
                    "HYUNDAI_MOTOR",
                    List.of("005380"),
                    List.of("현대차", "기아", "현대모비스", "현대글로비스", "현대로템", "현대위아", "현대오토에버", "현대제철", "현대건설", "현대비앤지스틸")
            ),
            KrxBrandFamily.prefix(
                    "HYUNDAI_DEPARTMENT",
                    List.of("005440", "069960"),
                    List.of("현대백화점", "현대그린푸드", "현대홈쇼핑", "현대이지웰", "현대리바트", "현대지에프홀딩스")
            ),
            KrxBrandFamily.prefix("HYUNDAI_MARINE_FIRE", List.of("001450"), List.of("현대해상"))
    );

    @Nullable
    public KrxBrandFamily resolve(StockSymbol stockSymbol) {
        String candidateName = resolveCommonStockName(stockSymbol.getName());
        String normalized = normalize(candidateName != null ? candidateName : stockSymbol.getName());
        if (!StringUtils.hasText(normalized)) {
            return null;
        }

        return FAMILY_RULES.stream()
                .filter(rule -> rule.matches(normalized))
                .findFirst()
                .orElse(null);
    }

    @Nullable
    public String resolveCommonStockName(@Nullable String stockName) {
        if (!StringUtils.hasText(stockName)) {
            return null;
        }

        String trimmed = stockName.trim();
        for (String suffix : PREFERRED_SUFFIXES) {
            if (trimmed.endsWith(suffix)) {
                return trimmed.substring(0, trimmed.length() - suffix.length()).trim();
            }
        }
        return null;
    }

    private String normalize(String input) {
        return input.replace(" ", "").trim().toUpperCase(Locale.ROOT);
    }

    public record KrxBrandFamily(
            String key,
            List<String> representativeTickers,
            List<String> namePrefixes,
            List<String> excludedPrefixes
    ) {
        static KrxBrandFamily prefix(String key, List<String> representativeTickers, List<String> namePrefixes) {
            return new KrxBrandFamily(key, representativeTickers, namePrefixes, List.of());
        }

        static KrxBrandFamily withExclusions(
                String key,
                List<String> representativeTickers,
                List<String> namePrefixes,
                List<String> excludedPrefixes
        ) {
            return new KrxBrandFamily(key, representativeTickers, namePrefixes, excludedPrefixes);
        }

        boolean matches(String normalizedStockName) {
            boolean included = namePrefixes.stream()
                    .map(prefix -> prefix.replace(" ", "").trim().toUpperCase(Locale.ROOT))
                    .anyMatch(normalizedStockName::startsWith);
            if (!included) {
                return false;
            }
            return excludedPrefixes.stream()
                    .map(prefix -> prefix.replace(" ", "").trim().toUpperCase(Locale.ROOT))
                    .noneMatch(normalizedStockName::startsWith);
        }
    }
}
