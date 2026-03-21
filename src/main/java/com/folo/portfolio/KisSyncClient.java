package com.folo.portfolio;

import java.util.List;

public interface KisSyncClient {

    List<KisSyncTradePayload> syncTrades(KisSyncSession session);
}
