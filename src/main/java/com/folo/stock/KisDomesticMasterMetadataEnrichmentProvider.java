package com.folo.stock;

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
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class KisDomesticMasterMetadataEnrichmentProvider implements StockMetadataEnrichmentProvider {

    private static final String DEFAULT_SOURCE_PAYLOAD_VERSION = "kis:domestic-master:v2";

    private final MarketDataSyncProperties properties;
    private volatile CachedMaster cachedMaster;

    public KisDomesticMasterMetadataEnrichmentProvider(MarketDataSyncProperties properties) {
        this.properties = properties;
    }

    @Override
    public StockDataProvider provider() {
        return StockDataProvider.KIS;
    }

    @Override
    public boolean isConfigured() {
        if (!properties.kis().enabled()) {
            return false;
        }

        String source = properties.kis().domesticMasterFileUrl();
        if (!StringUtils.hasText(source)) {
            return false;
        }

        return isRemoteSource(source) || Files.exists(Path.of(source));
    }

    @Override
    public boolean supports(MarketType market) {
        return market == MarketType.KRX;
    }

    @Override
    public StockMetadataEnrichmentRecord fetchMetadata(StockSymbol stockSymbol) {
        MetadataRow row = loadRows().get(stockSymbol.getTicker());
        if (row == null) {
            return new StockMetadataEnrichmentRecord(
                    null,
                    null,
                    StockClassificationScheme.KIS_MASTER,
                    DEFAULT_SOURCE_PAYLOAD_VERSION
            );
        }

        return new StockMetadataEnrichmentRecord(
                row.sectorName(),
                row.industryName(),
                StockClassificationScheme.KIS_MASTER,
                row.sourcePayloadVersion()
        );
    }

    private Map<String, MetadataRow> loadRows() {
        String source = properties.kis().domesticMasterFileUrl();
        if (!StringUtils.hasText(source)) {
            return Map.of();
        }

        FileTime lastModified = resolveLastModified(source);
        CachedMaster cached = cachedMaster;
        if (cached != null
                && cached.source().equals(source)
                && sameLastModified(cached.lastModified(), lastModified)) {
            return cached.rows();
        }

        synchronized (this) {
            cached = cachedMaster;
            if (cached != null
                    && cached.source().equals(source)
                    && sameLastModified(cached.lastModified(), lastModified)) {
                return cached.rows();
            }

            Map<String, MetadataRow> rows = loadRows(source);
            cachedMaster = new CachedMaster(source, lastModified, rows);
            return rows;
        }
    }

    private Map<String, MetadataRow> loadRows(String source) {
        Map<String, MetadataRow> rows = new LinkedHashMap<>();

        try (Reader reader = openReader(source)) {
            Iterable<CSVRecord> csvRecords = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .build()
                    .parse(reader);

            for (CSVRecord record : csvRecords) {
                String ticker = firstPresent(record, "ticker");
                if (!StringUtils.hasText(ticker)) {
                    continue;
                }

                String sectorName = firstPresent(record, "sectorName", "sector_name");
                String industryName = firstPresent(record, "industryName", "industry_name");
                String sourcePayloadVersion = firstPresent(
                        record,
                        "sourcePayloadVersion",
                        "source_payload_version"
                );

                rows.put(
                        ticker.trim().toUpperCase(),
                        new MetadataRow(
                                normalize(sectorName),
                                normalize(industryName),
                                normalize(sourcePayloadVersion) == null
                                        ? DEFAULT_SOURCE_PAYLOAD_VERSION
                                        : normalize(sourcePayloadVersion)
                        )
                );
            }
        } catch (IOException exception) {
            throw new IllegalStateException("KIS domestic master metadata enrichment failed", exception);
        }

        return rows;
    }

    private Reader openReader(String source) throws IOException {
        if (isRemoteSource(source)) {
            return new InputStreamReader(URI.create(source).toURL().openStream(), StandardCharsets.UTF_8);
        }

        return Files.newBufferedReader(Path.of(source), StandardCharsets.UTF_8);
    }

    private FileTime resolveLastModified(String source) {
        if (isRemoteSource(source)) {
            return null;
        }

        try {
            return Files.getLastModifiedTime(Path.of(source));
        } catch (IOException exception) {
            return null;
        }
    }

    private boolean sameLastModified(FileTime left, FileTime right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.equals(right);
    }

    private boolean isRemoteSource(String source) {
        return source.startsWith("http://") || source.startsWith("https://");
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

    private String normalize(String raw) {
        return StringUtils.hasText(raw) ? raw.trim() : null;
    }

    private record CachedMaster(
            String source,
            FileTime lastModified,
            Map<String, MetadataRow> rows
    ) {
    }

    private record MetadataRow(
            String sectorName,
            String industryName,
            String sourcePayloadVersion
    ) {
    }
}
