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
        return properties.kis().enabled() && StringUtils.hasText(properties.kis().masterFileUrl());
    }

    @Override
    public boolean supports(MarketType market) {
        return market == MarketType.KRX;
    }

    @Override
    public StockMasterSyncBatch fetchBatch(MarketType market, String cursor, int batchSize) {
        if (StringUtils.hasText(cursor)) {
            return new StockMasterSyncBatch(List.of(), null);
        }

        List<StockMasterSymbolRecord> records = new ArrayList<>();

        try (Reader reader = openReader(properties.kis().masterFileUrl())) {
            Iterable<CSVRecord> csvRecords = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .build()
                    .parse(reader);

            for (CSVRecord record : csvRecords) {
                String ticker = record.get("ticker");
                String name = record.get("name");

                if (!StringUtils.hasText(ticker) || !StringUtils.hasText(name)) {
                    continue;
                }

                records.add(new StockMasterSymbolRecord(
                        MarketType.KRX,
                        ticker,
                        name,
                        parseAssetType(record.isMapped("assetType") ? record.get("assetType") : null),
                        !record.isMapped("active") || Boolean.parseBoolean(record.get("active")),
                        record.isMapped("primaryExchangeCode") && StringUtils.hasText(record.get("primaryExchangeCode"))
                                ? record.get("primaryExchangeCode")
                                : "XKRX",
                        record.isMapped("currencyCode") && StringUtils.hasText(record.get("currencyCode"))
                                ? record.get("currencyCode")
                                : "KRW",
                        record.isMapped("sourceIdentifier") && StringUtils.hasText(record.get("sourceIdentifier"))
                                ? record.get("sourceIdentifier")
                                : ticker
                ));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("KIS stock master file sync failed", exception);
        }

        return new StockMasterSyncBatch(records, null);
    }

    private Reader openReader(String source) throws IOException {
        if (source.startsWith("http://") || source.startsWith("https://")) {
            return new InputStreamReader(URI.create(source).toURL().openStream(), StandardCharsets.UTF_8);
        }

        return Files.newBufferedReader(Path.of(source), StandardCharsets.UTF_8);
    }

    private AssetType parseAssetType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return AssetType.STOCK;
        }

        return "ETF".equalsIgnoreCase(raw.trim()) ? AssetType.ETF : AssetType.STOCK;
    }
}
