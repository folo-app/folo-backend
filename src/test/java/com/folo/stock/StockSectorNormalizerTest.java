package com.folo.stock;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockSectorNormalizerTest {

    @Test
    void normalizesKisAndKoreanFinancialLabelsToSharedCanonicalSector() {
        assertThat(StockSectorNormalizer.normalizeForMetadata(
                "Technology",
                "Semiconductors",
                StockClassificationScheme.KIS_MASTER
        )).isEqualTo(StockSectorNormalizer.INFORMATION_TECHNOLOGY);

        assertThat(StockSectorNormalizer.normalizeStoredSector("금융"))
                .isEqualTo(StockSectorNormalizer.FINANCIALS);
    }

    @Test
    void normalizesSicMetadataUsingIndustryKeywordsBeforeBroadDivision() {
        assertThat(StockSectorNormalizer.normalizeForMetadata(
                "Manufacturing",
                "SEMICONDUCTORS AND RELATED DEVICES",
                StockClassificationScheme.SIC
        )).isEqualTo(StockSectorNormalizer.INFORMATION_TECHNOLOGY);
    }
}
