package com.folo.portfolio;

import com.folo.common.enums.CurrencyCode;
import com.folo.common.enums.PortfolioVisibility;
import com.folo.common.enums.ReturnVisibility;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.follow.SocialRelationService;
import com.folo.user.User;
import com.folo.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final UserRepository userRepository;
    private final SocialRelationService socialRelationService;
    private final PortfolioValuationService portfolioValuationService;

    public PortfolioService(
            PortfolioRepository portfolioRepository,
            HoldingRepository holdingRepository,
            UserRepository userRepository,
            SocialRelationService socialRelationService,
            PortfolioValuationService portfolioValuationService
    ) {
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.userRepository = userRepository;
        this.socialRelationService = socialRelationService;
        this.portfolioValuationService = portfolioValuationService;
    }

    @Transactional(readOnly = true)
    public PortfolioResponse myPortfolio(Long userId) {
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        return buildPortfolioResponse(user, userId, true);
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

        return buildPortfolioResponse(target, currentUserId, currentUserId.equals(targetUserId));
    }

    private PortfolioResponse buildPortfolioResponse(User user, Long viewerUserId, boolean ownerView) {
        Portfolio portfolio = portfolioRepository.findByUserId(user.getId())
                .orElseGet(() -> portfolioRepository.save(Portfolio.defaultOf(user)));
        List<Holding> holdings = holdingRepository.findByUserIdOrderByIdAsc(user.getId());

        boolean fullyVisible = ownerView || user.getReturnVisibility() == ReturnVisibility.RATE_AND_AMOUNT;
        boolean rateOnly = !ownerView && user.getReturnVisibility() == ReturnVisibility.RATE_ONLY;

        CurrencyCode displayCurrency = resolveDisplayCurrency(viewerUserId, ownerView, portfolio);
        PortfolioValuationService.PortfolioValuationResult valuation = valuateWithFallback(holdings, displayCurrency);

        return new PortfolioResponse(
                portfolio.getId(),
                fullyVisible ? valuation.totalInvested() : null,
                fullyVisible ? valuation.totalValue() : null,
                fullyVisible ? valuation.totalReturn() : null,
                valuation.totalReturnRate(),
                fullyVisible ? valuation.dayReturn() : null,
                valuation.dayReturnRate(),
                maskHoldingItems(valuation.holdings(), rateOnly, fullyVisible),
                maskSectorAllocations(valuation.sectorAllocations(), fullyVisible),
                fullyVisible ? valuation.monthlyDividendForecasts() : List.of(),
                fullyVisible ? valuation.cashValue() : null,
                valuation.cashWeight(),
                portfolio.getSyncedAt() != null ? portfolio.getSyncedAt().toString() : null,
                valuation.displayCurrency().name(),
                valuation.fxAsOf() != null ? valuation.fxAsOf().toString() : null,
                valuation.fxStale(),
                fullyVisible
        );
    }

    private CurrencyCode resolveDisplayCurrency(Long viewerUserId, boolean ownerView, Portfolio targetPortfolio) {
        if (ownerView) {
            return targetPortfolio.getDisplayCurrency() != null
                    ? targetPortfolio.getDisplayCurrency()
                    : CurrencyCode.KRW;
        }

        return portfolioRepository.findByUserId(viewerUserId)
                .map(Portfolio::getDisplayCurrency)
                .orElse(CurrencyCode.KRW);
    }

    private PortfolioValuationService.PortfolioValuationResult valuateWithFallback(
            List<Holding> holdings,
            CurrencyCode requestedDisplayCurrency
    ) {
        try {
            return portfolioValuationService.valuate(holdings, requestedDisplayCurrency);
        } catch (ApiException exception) {
            if (exception.getErrorCode() != ErrorCode.INTERNAL_ERROR) {
                throw exception;
            }

            CurrencyCode singleNativeCurrency = resolveSingleNativeCurrency(holdings);
            if (singleNativeCurrency == null || singleNativeCurrency == requestedDisplayCurrency) {
                throw exception;
            }

            return portfolioValuationService.valuate(holdings, singleNativeCurrency);
        }
    }

    private CurrencyCode resolveSingleNativeCurrency(List<Holding> holdings) {
        CurrencyCode resolved = null;
        for (Holding holding : holdings) {
            CurrencyCode nativeCurrency = CurrencyCode.fromMarketAndRaw(
                    holding.getStockSymbol().getMarket(),
                    holding.getStockSymbol().getCurrencyCode()
            );
            if (resolved == null) {
                resolved = nativeCurrency;
                continue;
            }
            if (resolved != nativeCurrency) {
                return null;
            }
        }
        return resolved;
    }

    private List<PortfolioHoldingItem> maskHoldingItems(
            List<PortfolioHoldingItem> items,
            boolean rateOnly,
            boolean fullyVisible
    ) {
        return items.stream()
                .map(item -> new PortfolioHoldingItem(
                        item.holdingId(),
                        item.ticker(),
                        item.name(),
                        item.market(),
                        rateOnly ? null : item.quantity(),
                        rateOnly ? null : item.avgPrice(),
                        item.currentPrice(),
                        rateOnly ? null : item.totalInvested(),
                        rateOnly ? null : item.totalValue(),
                        rateOnly ? null : item.returnAmount(),
                        item.returnRate(),
                        item.weight(),
                        item.sectorCode(),
                        item.sectorName(),
                        item.assetType(),
                        item.currencyCode(),
                        item.annualDividendYield(),
                        item.dividendMonths(),
                        fullyVisible ? item.displayTotalInvested() : null,
                        fullyVisible ? item.displayTotalValue() : null,
                        fullyVisible ? item.displayReturnAmount() : null
                ))
                .toList();
    }

    private List<PortfolioAllocationItem> maskSectorAllocations(
            List<PortfolioAllocationItem> sectorAllocations,
            boolean fullyVisible
    ) {
        return sectorAllocations.stream()
                .map(item -> new PortfolioAllocationItem(
                        item.key(),
                        item.label(),
                        item.weight(),
                        fullyVisible ? item.value() : null
                ))
                .toList();
    }
}
