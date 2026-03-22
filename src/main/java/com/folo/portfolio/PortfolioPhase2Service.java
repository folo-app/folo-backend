package com.folo.portfolio;

import com.folo.common.enums.TradeSource;
import com.folo.common.enums.TradeVisibility;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.config.KisOAuthProperties;
import com.folo.security.FieldEncryptor;
import com.folo.stock.StockService;
import com.folo.trade.Trade;
import com.folo.trade.TradeRepository;
import com.folo.user.User;
import com.folo.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PortfolioPhase2Service {

    private final UserRepository userRepository;
    private final TradeRepository tradeRepository;
    private final StockService stockService;
    private final PortfolioProjectionService portfolioProjectionService;
    private final HoldingRepository holdingRepository;
    private final KisSyncClient kisSyncClient;
    private final FieldEncryptor fieldEncryptor;
    private final KisOAuthProperties kisOAuthProperties;

    public PortfolioPhase2Service(
            UserRepository userRepository,
            TradeRepository tradeRepository,
            StockService stockService,
            PortfolioProjectionService portfolioProjectionService,
            HoldingRepository holdingRepository,
            KisSyncClient kisSyncClient,
            FieldEncryptor fieldEncryptor,
            KisOAuthProperties kisOAuthProperties
    ) {
        this.userRepository = userRepository;
        this.tradeRepository = tradeRepository;
        this.stockService = stockService;
        this.portfolioProjectionService = portfolioProjectionService;
        this.holdingRepository = holdingRepository;
        this.kisSyncClient = kisSyncClient;
        this.fieldEncryptor = fieldEncryptor;
        this.kisOAuthProperties = kisOAuthProperties;
    }

    @Transactional
    public PortfolioSyncResponse sync(Long userId) {
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        KisSyncSession session = resolveSyncSession(user);
        List<KisSyncTradePayload> payloads = kisSyncClient.syncTrades(session);

        int syncedTrades = 0;
        for (KisSyncTradePayload payload : payloads) {
            boolean exists = tradeRepository.findByUserIdAndDeletedFalseOrderByTradedAtAscIdAsc(userId).stream()
                    .anyMatch(trade ->
                            trade.getStockSymbol().getTicker().equalsIgnoreCase(payload.ticker())
                                    && trade.getTradeType() == payload.tradeType()
                                    && trade.getQuantity().compareTo(payload.quantity()) == 0
                                    && trade.getPrice().compareTo(payload.price()) == 0
                                    && trade.getTradedAt().equals(payload.tradedAt()));
            if (exists) {
                continue;
            }
            tradeRepository.save(Trade.create(
                    user,
                    stockService.getRequiredSymbol(payload.market(), payload.ticker()),
                    payload.tradeType(),
                    payload.quantity(),
                    payload.price(),
                    payload.comment(),
                    payload.visibility() != null ? payload.visibility() : TradeVisibility.PRIVATE,
                    payload.tradedAt(),
                    TradeSource.KIS_SYNC
            ));
            syncedTrades++;
        }

        portfolioProjectionService.recalculate(userId);
        int syncedHoldings = holdingCount(userId);
        return new PortfolioSyncResponse(syncedHoldings, syncedTrades, LocalDateTime.now().toString());
    }

    private int holdingCount(Long userId) {
        return holdingRepository.findByUserIdOrderByIdAsc(userId).size();
    }

    private KisSyncSession resolveSyncSession(User user) {
        boolean hasOAuthConnection =
                user.getKisAccessTokenEncrypted() != null
                        && user.getKisPersonalSecretKeyEncrypted() != null
                        && kisOAuthProperties.appKey() != null
                        && kisOAuthProperties.appSecret() != null;
        if (hasOAuthConnection) {
            return new KisSyncSession(
                    kisOAuthProperties.appKey(),
                    kisOAuthProperties.appSecret(),
                    fieldEncryptor.decrypt(user.getKisAccessTokenEncrypted()),
                    fieldEncryptor.decrypt(user.getKisRefreshTokenEncrypted()),
                    fieldEncryptor.decrypt(user.getKisPersonalSecretKeyEncrypted()),
                    fieldEncryptor.decrypt(user.getKisAccountNumberEncrypted()),
                    user.getKisAccountProductCode()
            );
        }

        if (user.getKisAppKeyEncrypted() != null && user.getKisAppSecretEncrypted() != null) {
            return new KisSyncSession(
                    fieldEncryptor.decrypt(user.getKisAppKeyEncrypted()),
                    fieldEncryptor.decrypt(user.getKisAppSecretEncrypted()),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        throw new ApiException(ErrorCode.VALIDATION_ERROR, "KIS 연결 또는 앱키 등록을 먼저 완료해주세요.");
    }
}
