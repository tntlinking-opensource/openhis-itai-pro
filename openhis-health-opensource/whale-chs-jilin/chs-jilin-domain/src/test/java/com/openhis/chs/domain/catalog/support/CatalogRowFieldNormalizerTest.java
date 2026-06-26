package com.openhis.chs.domain.catalog.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogRowFieldNormalizerTest {

    @Test
    void normalizeChrgitmLv_mapsChineseLabelsToCodes() {
        assertThat(CatalogRowFieldNormalizer.normalizeChrgitmLv("甲")).isEqualTo("01");
        assertThat(CatalogRowFieldNormalizer.normalizeChrgitmLv("甲类")).isEqualTo("01");
        assertThat(CatalogRowFieldNormalizer.normalizeChrgitmLv("乙类")).isEqualTo("02");
        assertThat(CatalogRowFieldNormalizer.normalizeChrgitmLv("01")).isEqualTo("01");
    }

    @Test
    void normalizeChrgitmLv_truncatesLongPlatformText() {
        assertThat(CatalogRowFieldNormalizer.normalizeChrgitmLv("未知等级说明文字超过十六个字符占位"))
            .hasSize(16);
    }
}
