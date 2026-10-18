/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.support;

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

    @Test
    void truncateVarchar_truncatesToColumnWidth() {
        String longSpec = "规格".repeat(120);
        assertThat(CatalogRowFieldNormalizer.truncateVarchar(longSpec, 200))
            .hasSize(200)
            .isEqualTo(longSpec.substring(0, 200));
    }

    @Test
    void truncateVarchar_blankReturnsNull() {
        assertThat(CatalogRowFieldNormalizer.truncateVarchar("  ", 200)).isNull();
        assertThat(CatalogRowFieldNormalizer.truncateVarchar("null", 200)).isNull();
        assertThat(CatalogRowFieldNormalizer.truncateVarchar("--", 200)).isNull();
    }

    @Test
    void normalizeChrgitmLv_placeholderReturnsNull() {
        assertThat(CatalogRowFieldNormalizer.normalizeChrgitmLv("--")).isNull();
        assertThat(CatalogRowFieldNormalizer.normalizeChrgitmLv("null")).isNull();
    }
}
