package com.folo.trade;

import com.folo.common.api.ApiResponse;
import com.folo.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/trades")
public class TradeController {

    private final TradeService tradeService;

    public TradeController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TradeSummaryItem> create(@Valid @RequestBody CreateTradeRequest request) {
        return ApiResponse.success(tradeService.create(SecurityUtils.currentUserId(), request), "거래 기록이 생성되었습니다.");
    }

    @GetMapping("/me")
    public ApiResponse<TradeListResponse> myTrades(
            @RequestParam(required = false) String ticker,
            @RequestParam(required = false) String tradeType,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(tradeService.myTrades(SecurityUtils.currentUserId(), ticker, tradeType, from, to, page, size), "요청이 성공했습니다.");
    }

    @GetMapping("/{tradeId}")
    public ApiResponse<TradeDetailResponse> detail(@PathVariable Long tradeId) {
        return ApiResponse.success(tradeService.detail(SecurityUtils.currentUserId(), tradeId), "요청이 성공했습니다.");
    }

    @PatchMapping("/{tradeId}")
    public ApiResponse<TradeSummaryItem> update(@PathVariable Long tradeId, @Valid @RequestBody UpdateTradeRequest request) {
        return ApiResponse.success(tradeService.update(SecurityUtils.currentUserId(), tradeId, request), "거래 기록이 수정되었습니다.");
    }

    @DeleteMapping("/{tradeId}")
    public ApiResponse<Void> delete(@PathVariable Long tradeId) {
        tradeService.delete(SecurityUtils.currentUserId(), tradeId);
        return ApiResponse.successMessage("거래 기록이 삭제되었습니다.");
    }
}
