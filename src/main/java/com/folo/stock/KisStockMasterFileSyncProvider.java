package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import com.folo.config.MarketDataSyncProperties;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class KisStockMasterFileSyncProvider implements StockMasterSyncProvider {

    private final MarketDataSyncProperties properties;

    public KisStockMasterFileSyncProvider(MarketDataSyncProperties properties) {
        this.properties = properties;
    }

    @Override
    public StockDataProvider provider() {
        return StockDataProvider.KIS;
    }

    @Override
    public boolean isConfigured() {
        return properties.kis().enabled();
    }

    @Override
    public boolean supports(MarketType market) {
        return properties.kis().enabled() && StringUtils.hasText(resolveSource(market));
    }

    @Override
    public StockMasterSyncBatch fetchBatch(MarketType market, String cursor, int batchSize) {
        if (StringUtils.hasText(cursor)) {
            return new StockMasterSyncBatch(List.of(), null);
        }

        String source = resolveSource(market);
        if (!StringUtils.hasText(source)) {
            return new StockMasterSyncBatch(List.of(), null);
        }

        List<StockMasterSymbolRecord> records = new ArrayList<>();

        try (Reader reader = openReader(source)) {
            Iterable<CSVRecord> csvRecords = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .build()
                    .parse(reader);

            for (CSVRecord record : csvRecords) {
                StockMasterSymbolRecord symbolRecord = toRecord(market, record);
                if (symbolRecord != null) {
                    records.add(symbolRecord);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("KIS stock master file sync failed", exception);
        }

        return new StockMasterSyncBatch(records, null);
    }

    private StockMasterSymbolRecord toRecord(MarketType requestedMarket, CSVRecord record) {
        String ticker = firstPresent(record, "ticker", "pdno", "symb", "SYMB");
        String name = firstPresent(
                record,
                "name",
                "prdt_name",
                "ovrs_item_name",
                "prdt_abrv_name",
                "prdt_eng_name",
                "prdt_name120"
        );

        if (!StringUtils.hasText(ticker) || !StringUtils.hasText(name)) {
            return null;
        }

        MarketType resolvedMarket = resolveMarket(requestedMarket, record);
        if (resolvedMarket != requestedMarket) {
            return null;
        }

        String primaryExchangeCode = resolvePrimaryExchangeCode(resolvedMarket, record);
        return new StockMasterSymbolRecord(
                resolvedMarket,
                ticker.trim().toUpperCase(),
                name.trim(),
                parseAssetType(record),
                parseActive(record),
                primaryExchangeCode,
                resolveCurrencyCode(resolvedMarket, record),
                resolveSourceIdentifier(record, ticker)
        );
    }

    private String resolveSource(MarketType market) {
        return market == MarketType.KRX
                ? properties.kis().domesticMasterFileUrl()
                : properties.kis().overseasMasterFileUrl();
    }

    private Reader openReader(String source) throws IOException {
        if (source.startsWith("http://") || source.startsWith("https://")) {
            return new InputStreamReader(URI.create(source).toURL().openStream(), StandardCharsets.UTF_8);
        }

        return Files.newBufferedReader(Path.of(source), StandardCharsets.UTF_8);
    }

    private AssetType parseAssetType(CSVRecord record) {
        String raw = firstPresent(record, "assetType", "ovrs_stck_dvsn_cd", "scty_grp_id_cd", "etf_dvsn_cd");
        if (!StringUtils.hasText(raw)) {
            return AssetType.STOCK;
        }

        String normalized = raw.trim().toUpperCase();
        return normalized.contains("ETF")
                || normalized.equals("03")
                || normalized.equals("EF")
                || normalized.equals("FE")
                ? AssetType.ETF
                : AssetType.STOCK;
    }

    private boolean parseActive(CSVRecord record) {
        String raw = firstPresent(record, "active", "lstg_yn", "listed", "isActive");
        if (!StringUtils.hasText(raw)) {
            return true;
        }

        String normalized = raw.trim().toUpperCase();
        return normalized.equals("TRUE")
                || normalized.equals("Y")
                || normalized.equals("1");
    }

    private String resolveCurrencyCode(MarketType market, CSVRecord record) {
        String raw = firstPresent(record, "currencyCode", "tr_crcy_cd", "currency", "curr");
        if (StringUtils.hasText(raw)) {
            return raw.trim().toUpperCase();
        }

        return market == MarketType.KRX ? "KRW" : "USD";
    }

    private String resolveSourceIdentifier(CSVRecord record, String ticker) {
        String raw = firstPresent(record, "sourceIdentifier", "std_pdno", "isin", "figi");
        return StringUtils.hasText(raw) ? raw.trim() : ticker.trim().toUpperCase();
    }

    private String resolvePrimaryExchangeCode(MarketType market, CSVRecord record) {
        String raw = firstPresent(record, "primaryExchangeCode", "primary_exchange", "exchange", "ovrs_excg_cd", "EXCD");
        if (StringUtils.hasText(raw)) {
            return raw.trim().toUpperCase();
        }

        return switch (market) {
            case KRX -> "XKRX";
            case NASDAQ -> "NAS";
            case NYSE -> "NYS";
            case AMEX -> "AMS";
        };
    }

    private MarketType resolveMarket(MarketType requestedMarket, CSVRecord record) {
        if (requestedMarket == MarketType.KRX) {
            return MarketType.KRX;
        }

        String marketValue = firstPresent(record, "market", "marketType", "market_type");
        if (StringUtils.hasText(marketValue)) {
            MarketType parsed = parseMarket(marketValue);
            if (parsed != null) {
                return parsed;
            }
        }

        String exchangeValue = firstPresent(record, "primaryExchangeCode", "primary_exchange", "exchange", "ovrs_excg_cd", "EXCD");
        return parseMarket(exchangeValue);
    }

    private MarketType parseMarket(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }

        String normalized = raw.trim().toUpperCase();
        return switch (normalized) {
            case "KRX", "XKRX", "J" -> MarketType.KRX;
            case "NASDAQ", "XNAS", "NAS", "BAQ" -> MarketType.NASDAQ;
            case "NYSE", "XNYS", "NYS", "BAY" -> MarketType.NYSE;
            case "AMEX", "XASE", "AMS", "BAA" -> MarketType.AMEX;
            default -> null;
        };
    }

    private String firstPresent(CSVRecord record, String... columnNames) {
        for (String columnName : columnNames) {
            if (!record.isMapped(columnName)) {
                continue;
            }

            String value = record.get(columnName);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }

        return null;
    }
}
