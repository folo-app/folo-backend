package com.folo.stock;

import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

public record DividendEventRecord(
        StockDataProvider provider,
        @Nullable String sourceEventId,
        DividendEventType eventType,
        @Nullable LocalDate declaredDate,
        @Nullable LocalDate exDividendDate,
        @Nullable LocalDate recordDate,
        @Nullable LocalDate payDate,
        @Nullable BigDecimal cashAmount,
        @Nullable String currencyCode,
        @Nullable String frequencyRaw
) {

    public String dedupeKey(Long stockSymbolId) {
        String normalizedPayload;
        if (sourceEventId != null && !sourceEventId.isBlank()) {
            normalizedPayload = "provider=%s|source_event_id=%s".formatted(provider, sourceEventId.trim());
        } else {
            normalizedPayload = "provider=%s|symbol=%s|type=%s|ex=%s|pay=%s|amount=%s|currency=%s".formatted(
                    provider,
                    stockSymbolId,
                    eventType,
                    normalizeDate(exDividendDate),
                    normalizeDate(payDate),
                    normalizeAmount(cashAmount),
                    normalizeText(currencyCode)
            );
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizedPayload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", exception);
        }
    }

    private String normalizeDate(@Nullable LocalDate value) {
        return value == null ? "-" : value.toString();
    }

    private String normalizeAmount(@Nullable BigDecimal value) {
        return value == null ? "-" : value.setScale(6, RoundingMode.HALF_UP).toPlainString();
    }

    private String normalizeText(@Nullable String value) {
        return value == null || value.isBlank() ? "-" : value.trim().toUpperCase();
    }
}
