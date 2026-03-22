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
    private final KisDomesticDividendDebugService kisDomesticDividendDebugService;

    public StockEnrichmentOpsController(
            AppOpsProperties appOpsProperties,
            StockDividendEnrichmentService stockDividendEnrichmentService,
            StockMetadataEnrichmentService stockMetadataEnrichmentService,
            KisDomesticDividendDebugService kisDomesticDividendDebugService
    ) {
        this.appOpsProperties = appOpsProperties;
        this.stockDividendEnrichmentService = stockDividendEnrichmentService;
        this.stockMetadataEnrichmentService = stockMetadataEnrichmentService;
        this.kisDomesticDividendDebugService = kisDomesticDividendDebugService;
    }

    @PostMapping("/dividends/sync")
    public ApiResponse<StockEnrichmentSyncResponse> syncDividends(
            @RequestHeader(name = TRIGGER_SECRET_HEADER, required = false) String triggerSecret,
            @RequestBody(required = false) StockEnrichmentSyncRequest request
    ) {
        authorize(triggerSecret);
        SyncMode syncMode = resolveSyncMode(request);
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
        SyncMode syncMode = resolveSyncMode(request);
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

    private SyncMode resolveSyncMode(StockEnrichmentSyncRequest request) {
        List<Long> stockSymbolIds = request == null ? null : request.stockSymbolIds();
        return stockSymbolIds == null || stockSymbolIds.isEmpty()
                ? SyncMode.PRIORITY
                : SyncMode.EXPLICIT;
    }

    private enum SyncMode {
        PRIORITY,
        EXPLICIT
    }
}
