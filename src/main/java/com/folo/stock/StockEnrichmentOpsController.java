package com.folo.stock;

import com.folo.common.api.ApiResponse;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.config.AppOpsProperties;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/stock-enrichment")
public class StockEnrichmentOpsController {

    private static final String TRIGGER_SECRET_HEADER = "X-Internal-Trigger-Secret";

    private final AppOpsProperties appOpsProperties;
    private final StockDividendEnrichmentService stockDividendEnrichmentService;
    private final StockMetadataEnrichmentService stockMetadataEnrichmentService;
    private final StockIssuerProfileSyncService stockIssuerProfileSyncService;
    private final KisDomesticDividendDebugService kisDomesticDividendDebugService;

    public StockEnrichmentOpsController(
            AppOpsProperties appOpsProperties,
            StockDividendEnrichmentService stockDividendEnrichmentService,
            StockMetadataEnrichmentService stockMetadataEnrichmentService,
            StockIssuerProfileSyncService stockIssuerProfileSyncService,
            KisDomesticDividendDebugService kisDomesticDividendDebugService
    ) {
        this.appOpsProperties = appOpsProperties;
        this.stockDividendEnrichmentService = stockDividendEnrichmentService;
        this.stockMetadataEnrichmentService = stockMetadataEnrichmentService;
        this.stockIssuerProfileSyncService = stockIssuerProfileSyncService;
        this.kisDomesticDividendDebugService = kisDomesticDividendDebugService;
    }

    @PostMapping("/dividends/sync")
    public ApiResponse<StockEnrichmentSyncResponse> syncDividends(
            @RequestHeader(name = TRIGGER_SECRET_HEADER, required = false) String triggerSecret,
            @RequestBody(required = false) StockEnrichmentSyncRequest request
    ) {
        authorize(triggerSecret);
        SyncMode syncMode = resolveStandardSyncMode(request);
        if (syncMode == SyncMode.PRIORITY) {
            stockDividendEnrichmentService.syncPrioritySymbols();
        } else {
            stockDividendEnrichmentService.syncSymbols(request.stockSymbolIds());
        }

        return ApiResponse.success(
                new StockEnrichmentSyncResponse("DIVIDEND", syncMode.name(), syncMode == SyncMode.PRIORITY ? 0 : request.stockSymbolIds().size()),
                "배당 enrichment sync가 실행되었습니다."
        );
    }

    @PostMapping("/metadata/sync")
    public ApiResponse<StockEnrichmentSyncResponse> syncMetadata(
            @RequestHeader(name = TRIGGER_SECRET_HEADER, required = false) String triggerSecret,
            @RequestBody(required = false) StockEnrichmentSyncRequest request
    ) {
        authorize(triggerSecret);
        SyncMode syncMode = resolveStandardSyncMode(request);
        if (syncMode == SyncMode.PRIORITY) {
            stockMetadataEnrichmentService.syncPrioritySymbols();
        } else {
            stockMetadataEnrichmentService.syncSymbols(request.stockSymbolIds());
        }

        return ApiResponse.success(
                new StockEnrichmentSyncResponse("METADATA", syncMode.name(), syncMode == SyncMode.PRIORITY ? 0 : request.stockSymbolIds().size()),
                "메타데이터 enrichment sync가 실행되었습니다."
        );
    }

    @PostMapping("/issuer-profiles/sync")
    public ApiResponse<StockEnrichmentSyncResponse> syncIssuerProfiles(
            @RequestHeader(name = TRIGGER_SECRET_HEADER, required = false) String triggerSecret,
            @RequestBody(required = false) StockEnrichmentSyncRequest request
    ) {
        authorize(triggerSecret);
        SyncMode syncMode = resolveIssuerProfileSyncMode(request);
        switch (syncMode) {
            case PRIORITY -> stockIssuerProfileSyncService.syncPrioritySymbols();
            case EXPLICIT -> stockIssuerProfileSyncService.syncSymbols(request.stockSymbolIds());
            case FULL -> stockIssuerProfileSyncService.syncAllActiveSymbols();
            case MISSING -> stockIssuerProfileSyncService.syncMissingActiveSymbols();
        }

        return ApiResponse.success(
                new StockEnrichmentSyncResponse(
                        "ISSUER_PROFILE",
                        syncMode.name(),
                        requestedCount(syncMode, request)
                ),
                "OPENDART issuer profile sync가 실행되었습니다."
        );
    }

    @PostMapping("/dividends/debug/kis")
    public ApiResponse<KisDividendDebugResponse> debugKisDividends(
            @RequestHeader(name = TRIGGER_SECRET_HEADER, required = false) String triggerSecret,
            @RequestBody(required = false) KisDividendDebugRequest request
    ) {
        authorize(triggerSecret);
        return ApiResponse.success(
                kisDomesticDividendDebugService.capture(request),
                "KIS raw dividend payload를 캡처했습니다."
        );
    }

    private void authorize(String triggerSecret) {
        String configuredSecret = appOpsProperties.triggerSecret();
        if (!StringUtils.hasText(configuredSecret)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "내부 enrichment trigger가 비활성화되어 있습니다.");
        }
        if (!configuredSecret.equals(triggerSecret)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "내부 enrichment trigger 권한이 없습니다.");
        }
    }

    private SyncMode resolveStandardSyncMode(StockEnrichmentSyncRequest request) {
        List<Long> stockSymbolIds = request == null ? null : request.stockSymbolIds();
        if (stockSymbolIds != null && !stockSymbolIds.isEmpty()) {
            return SyncMode.EXPLICIT;
        }

        String rawMode = request == null ? null : request.mode();
        if (!StringUtils.hasText(rawMode)) {
            return SyncMode.PRIORITY;
        }

        SyncMode parsedMode = parseMode(rawMode);
        if (parsedMode != SyncMode.PRIORITY && parsedMode != SyncMode.EXPLICIT) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "해당 endpoint는 PRIORITY 또는 EXPLICIT 모드만 지원합니다.");
        }
        return parsedMode;
    }

    private SyncMode resolveIssuerProfileSyncMode(StockEnrichmentSyncRequest request) {
        List<Long> stockSymbolIds = request == null ? null : request.stockSymbolIds();
        if (stockSymbolIds != null && !stockSymbolIds.isEmpty()) {
            return SyncMode.EXPLICIT;
        }

        String rawMode = request == null ? null : request.mode();
        return StringUtils.hasText(rawMode) ? parseMode(rawMode) : SyncMode.PRIORITY;
    }

    private int requestedCount(SyncMode syncMode, StockEnrichmentSyncRequest request) {
        return syncMode == SyncMode.EXPLICIT && request != null && request.stockSymbolIds() != null
                ? request.stockSymbolIds().size()
                : 0;
    }

    private SyncMode parseMode(String rawMode) {
        try {
            return SyncMode.valueOf(rawMode.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 sync mode입니다: " + rawMode);
        }
    }

    private enum SyncMode {
        PRIORITY,
        EXPLICIT,
        FULL,
        MISSING
    }
}
