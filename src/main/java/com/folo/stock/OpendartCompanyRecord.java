package com.folo.stock;

import org.springframework.lang.Nullable;

public record OpendartCompanyRecord(
        String corpCode,
        @Nullable String corpName,
        @Nullable String stockCode,
        @Nullable String corpCls,
        @Nullable String hmUrl,
        @Nullable String irUrl,
        @Nullable String indutyCode,
        @Nullable String sourcePayloadVersion
) {
}
