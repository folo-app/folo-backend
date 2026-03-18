package com.folo.portfolio;

import com.folo.common.enums.PortfolioVisibility;
import com.folo.common.enums.ReturnVisibility;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.follow.SocialRelationService;
import com.folo.stock.PriceSnapshot;
import com.folo.stock.PriceSnapshotRepository;
import com.folo.user.User;
import com.folo.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final PriceSnapshotRepository priceSnapshotRepository;
    private final UserRepository userRepository;
    private final SocialRelationService socialRelationService;

    public PortfolioService(
            PortfolioRepository portfolioRepository,
            HoldingRepository holdingRepository,
            PriceSnapshotRepository priceSnapshotRepository,
            UserRepository userRepository,
            SocialRelationService socialRelationService
    ) {
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.priceSnapshotRepository = priceSnapshotRepository;
        this.userRepository = userRepository;
        this.socialRelationService = socialRelationService;
    }

    @Transactional(readOnly = true)
    public PortfolioResponse myPortfolio(Long userId) {
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        return buildPortfolioResponse(user, true);
    }

    @Transactional(readOnly = true)
    public PortfolioResponse userPortfolio(Long currentUserId, Long targetUserId) {
        User target = userRepository.findByIdAndActiveTrue(targetUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        boolean accessible = switch (target.getPortfolioVisibility()) {
            case PUBLIC -> true;
            case FRIENDS_ONLY -> socialRelationService.isMutualFollow(currentUserId, targetUserId);
            case PRIVATE -> currentUserId.equals(targetUserId);
        };

        if (!accessible) {
            throw new ApiException(ErrorCode.FORBIDDEN, "포트폴리오를 조회할 수 없습니다.");
        }

        return buildPortfolioResponse(target, currentUserId.equals(targetUserId));
    }

    private PortfolioResponse buildPortfolioResponse(User user, boolean ownerView) {
        Portfolio portfolio = portfolioRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "포트폴리오를 찾을 수 없습니다."));
        List<Holding> holdings = holdingRepository.findByUserIdOrderByIdAsc(user.getId());

        boolean fullyVisible = ownerView || user.getReturnVisibility() == ReturnVisibility.RATE_AND_AMOUNT;
        boolean rateOnly = !ownerView && user.getReturnVisibility() == ReturnVisibility.RATE_ONLY;

        List<PortfolioHoldingItem> items = holdings.stream()
                .map(holding -> {
                    PriceSnapshot snapshot = priceSnapshotRepository.findByStockSymbolId(holding.getStockSymbol().getId()).orElse(null);
                    BigDecimal currentPrice = snapshot != null ? snapshot.getCurrentPrice() : holding.getAvgPrice();
                    return new PortfolioHoldingItem(
                            holding.getId(),
                            holding.getStockSymbol().getTicker(),
                            holding.getStockSymbol().getName(),
                            holding.getStockSymbol().getMarket().name(),
                            rateOnly ? null : holding.getQuantity(),
                            rateOnly ? null : holding.getAvgPrice(),
                            currentPrice,
                            rateOnly ? null : holding.getTotalInvested(),
                            rateOnly ? null : holding.getTotalValue(),
                            rateOnly ? null : holding.getReturnAmount(),
                            holding.getReturnRate(),
                            holding.getWeight()
                    );
                })
                .toList();

        return new PortfolioResponse(
                portfolio.getId(),
                fullyVisible ? portfolio.getTotalInvested() : null,
                fullyVisible ? portfolio.getTotalValue() : null,
                fullyVisible ? portfolio.getTotalReturnAmount() : null,
                portfolio.getTotalReturnRate(),
                fullyVisible ? portfolio.getDayReturnAmount() : null,
                portfolio.getDayReturnRate(),
                items,
                portfolio.getSyncedAt() != null ? portfolio.getSyncedAt().toString() : null,
                fullyVisible
        );
    }
}
