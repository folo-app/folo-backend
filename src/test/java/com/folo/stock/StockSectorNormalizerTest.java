package com.folo.stock;

import com.folo.common.enums.AssetType;
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

        assertThat(StockSectorNormalizer.normalizeStoredSector("지주사"))
                .isEqualTo(StockSectorNormalizer.HOLDING_COMPANIES);

        assertThat(StockSectorNormalizer.normalizeForMetadata(
                "Conglomerates",
                "Conglomerates",
                StockClassificationScheme.KRX_SECTOR_MAP
        )).isEqualTo(StockSectorNormalizer.CONGLOMERATES);
    }

    @Test
    void normalizesSicMetadataUsingIndustryKeywordsBeforeBroadDivision() {
        assertThat(StockSectorNormalizer.normalizeForMetadata(
                "Manufacturing",
                "SEMICONDUCTORS AND RELATED DEVICES",
                StockClassificationScheme.SIC
        )).isEqualTo(StockSectorNormalizer.INFORMATION_TECHNOLOGY);
    }

    @Test
    void resolvesDefenseIndustryAndReturnsUnifiedKoreanLabel() {
        StockSectorNormalizer.ResolvedSector resolvedSector = StockSectorNormalizer.resolve(
                AssetType.STOCK,
                null,
                null,
                "Manufacturing",
                "Aerospace & Defense",
                StockClassificationScheme.SIC
        );

        assertThat(resolvedSector.code()).isEqualTo(StockSectorCode.INDUSTRIALS);
        assertThat(resolvedSector.label()).isEqualTo("산업재");
    }

    @Test
    void treatsCommunicationsEquipmentAsInformationTechnology() {
        StockSectorNormalizer.ResolvedSector resolvedSector = StockSectorNormalizer.resolve(
                AssetType.STOCK,
                null,
                null,
                "Information Technology",
                "Communications Equipment",
                StockClassificationScheme.KRX_SECTOR_MAP
        );

        assertThat(resolvedSector.code()).isEqualTo(StockSectorCode.INFORMATION_TECHNOLOGY);
        assertThat(resolvedSector.label()).isEqualTo("정보기술");
    }

    @Test
    void allowsBetterMetadataToOverrideStoredOther() {
        StockSectorNormalizer.ResolvedSector resolvedSector = StockSectorNormalizer.resolve(
                AssetType.STOCK,
                StockSectorCode.OTHER,
                "기타",
                "Technology",
                "Semiconductors",
                StockClassificationScheme.KRX_SECTOR_MAP
        );

        assertThat(resolvedSector.code()).isEqualTo(StockSectorCode.INFORMATION_TECHNOLOGY);
        assertThat(resolvedSector.label()).isEqualTo("정보기술");
    }
}
