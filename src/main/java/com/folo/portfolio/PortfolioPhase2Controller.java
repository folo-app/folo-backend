package com.folo.portfolio;

import com.folo.common.api.ApiResponse;
import com.folo.security.SecurityUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/portfolio")
public class PortfolioPhase2Controller {

    private final PortfolioPhase2Service portfolioPhase2Service;

    public PortfolioPhase2Controller(PortfolioPhase2Service portfolioPhase2Service) {
        this.portfolioPhase2Service = portfolioPhase2Service;
    }

    @PostMapping("/sync")
    public ApiResponse<PortfolioSyncResponse> sync() {
        return ApiResponse.success(portfolioPhase2Service.sync(SecurityUtils.currentUserId()), "포트폴리오가 동기화되었습니다.");
    }
}
