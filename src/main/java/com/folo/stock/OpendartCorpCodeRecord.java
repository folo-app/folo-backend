package com.folo.stock;

import org.springframework.lang.Nullable;

import java.time.LocalDate;

public record OpendartCorpCodeRecord(
        String corpCode,
        @Nullable String corpName,
        String stockCode,
        @Nullable LocalDate modifyDate,
        String sourcePayloadVersion
) {
}
