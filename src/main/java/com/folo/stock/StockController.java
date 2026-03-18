package com.folo.stock;

import com.folo.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stocks")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/search")
    public ApiResponse<StockSearchResponse> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "ALL") String market
    ) {
        return ApiResponse.success(stockService.search(q, market), "요청이 성공했습니다.");
    }

    @GetMapping("/{ticker}/price")
    public ApiResponse<StockPriceResponse> getPrice(
            @PathVariable String ticker,
            @RequestParam String market
    ) {
        return ApiResponse.success(stockService.getPrice(ticker, market), "요청이 성공했습니다.");
    }
}
