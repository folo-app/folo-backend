package com.folo.portfolio;

import com.folo.common.enums.MarketType;
import com.folo.common.enums.TradeType;
import com.folo.common.enums.TradeVisibility;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record KisSyncTradePayload(
        String ticker,
        MarketType market,
        TradeType tradeType,
        BigDecimal quantity,
        BigDecimal price,
        @Nullable String comment,
        @Nullable TradeVisibility visibility,
        LocalDateTime tradedAt
) {
}
