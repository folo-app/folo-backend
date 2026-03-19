package com.folo.integration.kis;

import com.folo.common.api.ApiResponse;
import com.folo.security.SecurityUtils;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/integrations/kis/connect")
public class KisConnectionController {

    private final KisConnectionService kisConnectionService;

    public KisConnectionController(KisConnectionService kisConnectionService) {
        this.kisConnectionService = kisConnectionService;
    }

    @GetMapping("/status")
    public ApiResponse<KisConnectionStatusResponse> status() {
        return ApiResponse.success(
                kisConnectionService.getStatus(SecurityUtils.currentUserId()),
                "요청이 성공했습니다."
        );
    }

    @PostMapping("/start")
    public ApiResponse<KisConnectionStartResponse> start() {
        return ApiResponse.success(
                kisConnectionService.start(SecurityUtils.currentUserId()),
                "KIS 연결 시작 skeleton 응답입니다."
        );
    }

    @GetMapping("/callback")
    public ApiResponse<KisConnectionCallbackResponse> callback(
            @Nullable @RequestParam(value = "authorization_code", required = false) String authorizationCode,
            @Nullable @RequestParam(value = "acctno", required = false) String accountNumber,
            @Nullable @RequestParam(value = "acct_prdt_cd", required = false) String accountProductCode,
            @Nullable @RequestParam(value = "partner_user", required = false) String partnerUser,
            @Nullable @RequestParam(value = "personalseckey", required = false) String personalSecKey,
            @Nullable @RequestParam(required = false) String state,
            @Nullable @RequestParam(required = false) String error
    ) {
        return ApiResponse.success(
                kisConnectionService.callback(
                        authorizationCode,
                        accountNumber,
                        accountProductCode,
                        partnerUser,
                        personalSecKey,
                        state,
                        error
                ),
                "KIS callback skeleton 응답입니다."
        );
    }

    @DeleteMapping
    public ApiResponse<Void> disconnect() {
        kisConnectionService.disconnect(SecurityUtils.currentUserId());
        return ApiResponse.successMessage("KIS 연결 skeleton 해제 요청을 처리했습니다.");
    }
}
