package com.folo.integration.kis;

import com.folo.common.api.ApiResponse;
import com.folo.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
    public ApiResponse<KisConnectionStartResponse> start(@Valid @RequestBody KisConnectionStartRequest request) {
        return ApiResponse.success(
                kisConnectionService.start(SecurityUtils.currentUserId(), request),
                "KIS 연결 시작 응답입니다."
        );
    }

    @GetMapping(value = "/authorize", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> authorize(@RequestParam String state) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(kisConnectionService.renderAuthorizationPage(state));
    }

    @GetMapping(value = "/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> callbackGet(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(kisConnectionService.renderCallbackPage(params));
    }

    @PostMapping(
            value = "/callback",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_HTML_VALUE
    )
    public ResponseEntity<String> callbackPostForm(@RequestParam Map<String, String> payload) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(kisConnectionService.renderCallbackPage(payload));
    }

    @PostMapping(
            value = "/callback",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_HTML_VALUE
    )
    public ResponseEntity<String> callbackPostJson(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        if (payload != null) {
            payload.forEach((key, value) -> values.put(key, value == null ? null : value.toString()));
        }

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(kisConnectionService.renderCallbackPage(values));
    }

    @DeleteMapping
    public ApiResponse<Void> disconnect() {
        kisConnectionService.disconnect(SecurityUtils.currentUserId());
        return ApiResponse.successMessage("KIS 연결이 해제되었습니다.");
    }
}
