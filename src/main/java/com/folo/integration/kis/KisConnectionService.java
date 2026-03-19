package com.folo.integration.kis;

import com.folo.config.KisOAuthProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class KisConnectionService {

    private final KisOAuthProperties properties;

    public KisConnectionService(KisOAuthProperties properties) {
        this.properties = properties;
    }

    public KisConnectionStatusResponse getStatus(Long userId) {
        boolean configured = isConfigured();
        boolean available = properties.enabled() && configured;
        String phase = available ? "READY" : properties.enabled() ? "CONFIG_MISSING" : "PREPARING";
        String nextStep = available
                ? "OAuth 연결 시작 엔드포인트와 callback exchange 구현을 이어서 붙이면 됩니다."
                : "서비스 공용 KIS 앱키, 시크릿, redirect URI, corp number를 설정해야 합니다.";

        return new KisConnectionStatusResponse(
                false,
                phase,
                properties.enabled(),
                configured,
                available,
                null,
                nextStep
        );
    }

    public KisConnectionStartResponse start(Long userId) {
        boolean configured = isConfigured();
        boolean available = properties.enabled() && configured;
        String state = available ? UUID.randomUUID().toString() : null;
        Map<String, String> requestFields = available ? buildRequestFields(userId, state) : null;

        return new KisConnectionStartResponse(
                available,
                available ? "READY" : properties.enabled() ? "CONFIG_MISSING" : "PREPARING",
                available ? properties.baseUrl() + "/oauth2/authorizeP" : null,
                available ? "POST" : null,
                requestFields,
                state,
                available
                        ? "authorizeP POST form field skeleton까지 준비되었습니다. 고객 실명/휴대폰번호와 callback token exchange를 붙이면 됩니다."
                        : "KIS OAuth 환경 설정이 아직 준비되지 않았습니다."
        );
    }

    public KisConnectionCallbackResponse callback(
            String authorizationCode,
            String accountNumber,
            String accountProductCode,
            String partnerUser,
            String personalSecKey,
            String state,
            String error
    ) {
        return new KisConnectionCallbackResponse(
                false,
                "PREPARING",
                authorizationCode != null,
                state != null,
                authorizationCode,
                accountNumber,
                accountProductCode,
                partnerUser,
                personalSecKey,
                error,
                "callback payload skeleton only"
        );
    }

    public void disconnect(Long userId) {
        // OAuth 토큰/계좌 연결 저장소가 아직 없어 no-op skeleton으로 둔다.
    }

    private boolean isConfigured() {
        return StringUtils.hasText(properties.baseUrl())
                && StringUtils.hasText(properties.corpName())
                && StringUtils.hasText(properties.contractType())
                && StringUtils.hasText(properties.appKey())
                && StringUtils.hasText(properties.appSecret())
                && StringUtils.hasText(properties.redirectUri())
                && StringUtils.hasText(properties.corpNo());
    }

    private Map<String, String> buildRequestFields(Long userId, String state) {
        Map<String, String> requestFields = new LinkedHashMap<>();
        requestFields.put("appkey", properties.appKey());
        requestFields.put("redirect_uri", properties.redirectUri());
        requestFields.put("response_type", "code");
        requestFields.put("service", "2");
        requestFields.put("corpno", properties.corpNo());
        requestFields.put("partner_user", "folo-user-" + userId);
        requestFields.put("personalname", "FOLO USER");
        requestFields.put("personalphone", "01000000000");
        requestFields.put("roption", "P");
        requestFields.put("corp_name", properties.corpName());
        requestFields.put("state", state);
        requestFields.put("contract_type", properties.contractType());
        requestFields.put("isa_yn", "N");
        requestFields.put("reregist_yn", "N");
        return requestFields;
    }
}
