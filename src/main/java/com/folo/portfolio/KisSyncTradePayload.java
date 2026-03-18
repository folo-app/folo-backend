package com.folo.portfolio;

import com.folo.common.enums.MarketType;
import com.folo.common.enums.TradeType;
import com.folo.common.enums.TradeVisibility;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record KisSyncTradePayload(
        String ticker,
        MarketType market,
        TradeType tradeType,
        BigDecimal quantity,
        BigDecimal price,
        String comment,
        TradeVisibility visibility,
        LocalDateTime tradedAt
) {
}
