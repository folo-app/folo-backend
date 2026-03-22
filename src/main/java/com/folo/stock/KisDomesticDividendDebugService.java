package com.folo.stock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class KisDomesticDividendDebugService {

    private static final Logger log = LoggerFactory.getLogger(KisDomesticDividendDebugService.class);
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final KisDomesticDividendSyncProvider provider;
    private final StockSymbolRepository stockSymbolRepository;
    private final ObjectMapper objectMapper;
    private final Path debugRootDirectory;

    @Autowired
    public KisDomesticDividendDebugService(
            KisDomesticDividendSyncProvider provider,
            StockSymbolRepository stockSymbolRepository,
            ObjectMapper objectMapper
    ) {
        this(provider, stockSymbolRepository, objectMapper, Path.of(System.getProperty("java.io.tmpdir"), "folo-debug"));
    }

    KisDomesticDividendDebugService(
            KisDomesticDividendSyncProvider provider,
            StockSymbolRepository stockSymbolRepository,
            ObjectMapper objectMapper,
            Path debugRootDirectory
    ) {
        this.provider = provider;
        this.stockSymbolRepository = stockSymbolRepository;
        this.objectMapper = objectMapper;
        this.debugRootDirectory = debugRootDirectory;
    }

    public KisDividendDebugResponse capture(KisDividendDebugRequest request) {
        if (!provider.isConfigured()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "KIS dividend debug가 비활성화되어 있습니다.");
        }

        String ticker = resolveTicker(request);
        LocalDate fromDate = request != null && request.fromDate() != null
                ? request.fromDate()
                : LocalDate.now().minusYears(3);
        LocalDate toDate = request != null && request.toDate() != null
                ? request.toDate()
                : LocalDate.now();
        if (toDate.isBefore(fromDate)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "toDate는 fromDate보다 빠를 수 없습니다.");
        }

        String highGb = request == null ? null : request.highGb();
        JsonNode payload = provider.fetchRawPayload(ticker, fromDate, toDate, highGb);
        JsonNode rows = resolveRows(payload);

        Path savedPath = writePayload(ticker, payload);
        List<String> topLevelKeys = fieldNames(payload);
        List<String> firstRowKeys = rows != null && rows.isArray() && !rows.isEmpty()
                ? fieldNames(rows.get(0))
                : List.of();

        log.info(
                "Captured raw KIS dividend payload for ticker={} rows={} path={}",
                ticker,
                rows != null && rows.isArray() ? rows.size() : 0,
                savedPath
        );

        return new KisDividendDebugResponse(
                ticker,
                savedPath.toString(),
                rows != null && rows.isArray() ? rows.size() : 0,
                topLevelKeys,
                firstRowKeys
        );
    }

    private String resolveTicker(KisDividendDebugRequest request) {
        if (request != null && request.stockSymbolId() != null) {
            StockSymbol stockSymbol = stockSymbolRepository.findById(request.stockSymbolId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "stockSymbolId에 해당하는 종목이 없습니다."));
            return stockSymbol.getTicker();
        }

        if (request != null && StringUtils.hasText(request.ticker())) {
            return request.ticker().trim().toUpperCase();
        }

        throw new ApiException(ErrorCode.VALIDATION_ERROR, "stockSymbolId 또는 ticker 중 하나는 필요합니다.");
    }

    private JsonNode resolveRows(JsonNode response) {
        if (response == null || response.isNull()) {
            return null;
        }

        JsonNode output1 = response.get("output1");
        if (output1 != null && output1.isArray()) {
            return output1;
        }

        JsonNode output = response.get("output");
        if (output != null && output.isArray()) {
            return output;
        }

        return null;
    }

    private Path writePayload(String ticker, JsonNode payload) {
        try {
            Path outputDir = debugRootDirectory.resolve("kis-dividend");
            Files.createDirectories(outputDir);
            String fileName = "%s-%s.json".formatted(
                    ticker,
                    LocalDateTime.now().format(FILE_TIMESTAMP)
            );
            Path outputPath = outputDir.resolve(fileName);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), payload);
            return outputPath;
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "KIS raw payload를 저장하지 못했습니다.");
        }
    }

    private List<String> fieldNames(JsonNode node) {
        if (node == null || !node.isObject()) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        Iterator<String> iterator = node.fieldNames();
        while (iterator.hasNext()) {
            names.add(iterator.next());
        }
        return names;
    }
}
