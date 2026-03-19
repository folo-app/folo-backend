package com.folo.stock;

import com.folo.common.api.ApiResponse;
import com.folo.common.enums.MarketType;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/stocks")
public class StockController {

    private final StockService stockService;
    private final StockBrandingService stockBrandingService;

    public StockController(StockService stockService, StockBrandingService stockBrandingService) {
        this.stockService = stockService;
        this.stockBrandingService = stockBrandingService;
    }

    @GetMapping("/search")
    public ApiResponse<StockSearchResponse> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "ALL") String market
    ) {
        return ApiResponse.success(stockService.search(q, market), "요청이 성공했습니다.");
    }

    @GetMapping("/discover")
    public ApiResponse<StockDiscoverResponse> discover(
            @RequestParam(defaultValue = "12") int limit
    ) {
        return ApiResponse.success(stockService.discover(limit), "요청이 성공했습니다.");
    }

    @GetMapping("/{ticker}/logo")
    public ResponseEntity<byte[]> getLogo(
            @PathVariable String ticker,
            @RequestParam String market,
            @RequestParam(required = false) String micCode
    ) {
        StockBrandingService.LogoPayload payload = stockBrandingService.fetchLogo(
                ticker,
                MarketType.valueOf(market.toUpperCase()),
                micCode
        );

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(payload.bytes());
    }

    @GetMapping("/{ticker}/price")
    public ApiResponse<StockPriceResponse> getPrice(
            @PathVariable String ticker,
            @RequestParam String market
    ) {
        return ApiResponse.success(stockService.getPrice(ticker, market), "요청이 성공했습니다.");
    }
}
