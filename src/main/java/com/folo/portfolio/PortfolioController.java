package com.folo.portfolio;

import com.folo.common.api.ApiResponse;
import com.folo.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    public ApiResponse<PortfolioResponse> myPortfolio() {
        return ApiResponse.success(portfolioService.myPortfolio(SecurityUtils.currentUserId()), "요청이 성공했습니다.");
    }

    @GetMapping("/{userId}")
    public ApiResponse<PortfolioResponse> userPortfolio(@PathVariable Long userId) {
        return ApiResponse.success(portfolioService.userPortfolio(SecurityUtils.currentUserId(), userId), "요청이 성공했습니다.");
    }
}
