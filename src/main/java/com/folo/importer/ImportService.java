package com.folo.importer;

import com.folo.common.enums.ImportStatus;
import com.folo.common.enums.ImportType;
import com.folo.common.enums.MarketType;
import com.folo.common.enums.TradeSource;
import com.folo.common.enums.TradeType;
import com.folo.common.enums.TradeVisibility;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.portfolio.PortfolioProjectionService;
import com.folo.stock.StockService;
import com.folo.stock.StockSymbol;
import com.folo.trade.Trade;
import com.folo.trade.TradeRepository;
import com.folo.user.User;
import com.folo.user.UserRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ImportService {

    private static final Pattern OCR_FILENAME_PATTERN = Pattern.compile(
            "(?<ticker>[A-Za-z0-9]+)_(?<market>KRX|NASDAQ|NYSE|AMEX)_(?<tradeType>BUY|SELL)_(?<quantity>[0-9]+(?:\\.[0-9]+)?)_(?<price>[0-9]+(?:\\.[0-9]+)?)_(?<tradedAt>[0-9]{14}).*",
            Pattern.CASE_INSENSITIVE
    );

    private final ImportJobRepository importJobRepository;
    private final ImportResultRepository importResultRepository;
    private final UserRepository userRepository;
    private final StockService stockService;
    private final TradeRepository tradeRepository;
    private final PortfolioProjectionService portfolioProjectionService;

    public ImportService(
            ImportJobRepository importJobRepository,
            ImportResultRepository importResultRepository,
            UserRepository userRepository,
            StockService stockService,
            TradeRepository tradeRepository,
            PortfolioProjectionService portfolioProjectionService
    ) {
        this.importJobRepository = importJobRepository;
        this.importResultRepository = importResultRepository;
        this.userRepository = userRepository;
        this.stockService = stockService;
        this.tradeRepository = tradeRepository;
        this.portfolioProjectionService = portfolioProjectionService;
    }

    @Transactional
    public CsvImportResponse importCsv(Long userId, MultipartFile file, @Nullable String brokerCode) {
        User user = getUser(userId);
        ImportJob job = createJob(user, ImportType.CSV, brokerCode, file.getOriginalFilename());
        job.setStatus(ImportStatus.PROCESSING);

        List<ImportResult> results = new ArrayList<>();
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            for (CSVRecord record : parser) {
                results.add(parseCsvRecord(job, record));
            }
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "CSV를 파싱할 수 없습니다: " + exception.getMessage());
        }

        persistJobResults(job, results);
        return toCsvResponse(job, results);
    }

    @Transactional
    public OcrImportResponse importOcr(Long userId, MultipartFile image) {
        User user = getUser(userId);
        ImportJob job = createJob(user, ImportType.OCR, null, image.getOriginalFilename());
        job.setStatus(ImportStatus.PROCESSING);

        ImportResult result = parseOcrFilename(job, image.getOriginalFilename());
        persistJobResults(job, List.of(result));

        OcrImportParsedTrade parsed = result.isValid()
                ? new OcrImportParsedTrade(
                result.getId(),
                result.getStockSymbol().getTicker(),
                result.getStockSymbol().getName(),
                result.getTradeType().name(),
                result.getQuantity(),
                result.getPrice(),
                result.getTradedAt().toString()
        )
                : null;

        return new OcrImportResponse(job.getId(), parsed, result.isValid() ? 0.95 : 0.0);
    }

    @Transactional
    public ConfirmImportResponse confirmImport(Long userId, ConfirmImportRequest request) {
        if (request.importResultIds() == null || request.importResultIds().isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "importResultIds는 비어 있을 수 없습니다.");
        }

        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(request.importResultIds());
        if (uniqueIds.size() != request.importResultIds().size()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "중복된 importResultId는 허용되지 않습니다.");
        }

        Map<Long, ImportResult> resultById = importResultRepository.findAllById(uniqueIds).stream()
                .collect(Collectors.toMap(ImportResult::getId, Function.identity()));
        if (resultById.size() != uniqueIds.size()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "import result를 찾을 수 없습니다.");
        }

        List<ImportResult> results = uniqueIds.stream()
                .map(resultById::get)
                .toList();

        ImportJob job = results.get(0).getImportJob();
        if (!job.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }

        for (ImportResult result : results) {
            if (!result.getImportJob().getUser().getId().equals(userId)) {
                throw new ApiException(ErrorCode.FORBIDDEN);
            }
            if (!result.getImportJob().getId().equals(job.getId())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "같은 import job의 결과만 함께 확정할 수 있습니다.");
            }
            if (result.getConfirmedTradeId() != null) {
                throw new ApiException(ErrorCode.DUPLICATE, "이미 확정된 import result가 포함되어 있습니다.");
            }
        }

        List<ImportResult> validResults = results.stream()
                .filter(ImportResult::isValid)
                .toList();
        if (validResults.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "확정 가능한 import result가 없습니다.");
        }

        int savedTrades = 0;
        LocalDateTime confirmedAt = LocalDateTime.now();
        List<Long> confirmedImportResultIds = new ArrayList<>();
        List<Long> tradeIds = new ArrayList<>();
        for (ImportResult result : validResults) {

            Trade trade = Trade.create(
                    job.getUser(),
                    result.getStockSymbol(),
                    result.getTradeType(),
                    result.getQuantity(),
                    result.getPrice(),
                    result.getComment(),
                    result.getVisibility() != null ? result.getVisibility() : TradeVisibility.PRIVATE,
                    result.getTradedAt(),
                    job.getImportType() == ImportType.CSV ? TradeSource.CSV_IMPORT : TradeSource.OCR_IMPORT
            );
            tradeRepository.save(trade);
            result.setConfirmedTradeId(trade.getId());
            result.setConfirmedAt(confirmedAt);
            confirmedImportResultIds.add(result.getId());
            tradeIds.add(trade.getId());
            savedTrades++;
        }

        job.setStatus(ImportStatus.CONFIRMED);
        portfolioProjectionService.recalculate(userId);
        return new ConfirmImportResponse(
                savedTrades,
                confirmedImportResultIds,
                tradeIds
        );
    }

    private ImportJob createJob(User user, ImportType importType, @Nullable String brokerCode, @Nullable String filename) {
        ImportJob job = new ImportJob();
        job.setUser(user);
        job.setImportType(importType);
        job.setStatus(ImportStatus.PENDING);
        job.setBrokerCode(brokerCode);
        job.setSourceFileUrl(filename != null ? "upload://" + filename : null);
        job.setParsedCount(0);
        job.setFailedCount(0);
        return importJobRepository.save(job);
    }

    private void persistJobResults(ImportJob job, List<ImportResult> results) {
        results.forEach(importResultRepository::save);
        int parsedCount = (int) results.stream().filter(ImportResult::isValid).count();
        int failedCount = results.size() - parsedCount;
        job.setParsedCount(parsedCount);
        job.setFailedCount(failedCount);
        job.setCompletedAt(LocalDateTime.now());
        job.setStatus(failedCount == results.size() ? ImportStatus.FAILED : ImportStatus.COMPLETED);
    }

    private CsvImportResponse toCsvResponse(ImportJob job, List<ImportResult> results) {
        return new CsvImportResponse(
                job.getId(),
                job.getParsedCount(),
                job.getFailedCount(),
                results.stream().map(this::toPreviewItem).toList()
        );
    }

    private ImportPreviewItem toPreviewItem(ImportResult result) {
        return new ImportPreviewItem(
                result.getId(),
                result.getStockSymbol() != null ? result.getStockSymbol().getTicker() : null,
                result.getStockSymbol() != null ? result.getStockSymbol().getName() : null,
                result.getStockSymbol() != null ? result.getStockSymbol().getMarket().name() : null,
                result.getTradeType() != null ? result.getTradeType().name() : null,
                result.getQuantity(),
                result.getPrice(),
                result.getTradedAt() != null ? result.getTradedAt().toString() : null,
                result.isValid(),
                result.getErrorMessage(),
                result.isSelected()
        );
    }

    private ImportResult parseCsvRecord(ImportJob job, CSVRecord record) {
        ImportResult result = new ImportResult();
        result.setImportJob(job);
        result.setSelected(true);
        result.setVisibility(TradeVisibility.PRIVATE);

        try {
            Map<String, String> values = record.toMap().entrySet().stream()
                    .collect(Collectors.toMap(entry -> normalize(entry.getKey()), Map.Entry::getValue));

            String ticker = required(values, "ticker");
            MarketType market = MarketType.valueOf(required(values, "market").toUpperCase(Locale.ROOT));
            StockSymbol symbol = stockService.getRequiredSymbol(market, ticker);
            result.setStockSymbol(symbol);
            result.setTradeType(TradeType.valueOf(required(values, "tradetype").toUpperCase(Locale.ROOT)));
            result.setQuantity(new BigDecimal(required(values, "quantity")));
            result.setPrice(new BigDecimal(required(values, "price")));
            result.setTradedAt(LocalDateTime.parse(required(values, "tradedat")));
            result.setComment(optional(values, "comment").orElse(null));
            result.setVisibility(optional(values, "visibility")
                    .map(value -> TradeVisibility.valueOf(value.toUpperCase(Locale.ROOT)))
                    .orElse(TradeVisibility.PRIVATE));
            result.setValid(true);
            result.setErrorMessage(null);
        } catch (Exception exception) {
            result.setValid(false);
            result.setErrorMessage(exception.getMessage());
        }
        return result;
    }

    private ImportResult parseOcrFilename(ImportJob job, @Nullable String originalFilename) {
        ImportResult result = new ImportResult();
        result.setImportJob(job);
        result.setSelected(true);
        result.setVisibility(TradeVisibility.PRIVATE);

        Matcher matcher = OCR_FILENAME_PATTERN.matcher(originalFilename == null ? "" : originalFilename);
        if (!matcher.matches()) {
            result.setValid(false);
            result.setErrorMessage("OCR stub filename format must be TICKER_MARKET_TYPE_QTY_PRICE_yyyyMMddHHmmss.ext");
            return result;
        }

        try {
            MarketType market = MarketType.valueOf(matcher.group("market").toUpperCase(Locale.ROOT));
            StockSymbol stockSymbol = stockService.getRequiredSymbol(market, matcher.group("ticker"));
            result.setStockSymbol(stockSymbol);
            result.setTradeType(TradeType.valueOf(matcher.group("tradeType").toUpperCase(Locale.ROOT)));
            result.setQuantity(new BigDecimal(matcher.group("quantity")));
            result.setPrice(new BigDecimal(matcher.group("price")));
            result.setTradedAt(LocalDateTime.parse(matcher.group("tradedAt"), DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
            result.setValid(true);
        } catch (Exception exception) {
            result.setValid(false);
            result.setErrorMessage(exception.getMessage());
        }
        return result;
    }

    private String normalize(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("필수 컬럼이 누락되었습니다: " + key);
        }
        return value;
    }

    private Optional<String> optional(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private User getUser(Long userId) {
        return userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
