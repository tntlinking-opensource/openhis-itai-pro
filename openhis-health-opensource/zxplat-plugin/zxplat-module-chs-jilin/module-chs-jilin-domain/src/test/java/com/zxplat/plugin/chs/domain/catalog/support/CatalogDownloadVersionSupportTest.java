/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogDownloadVersionSupportTest {

    @Test
    void normalizeForPlatformRequest_keepsFullVersionName() {
        String longVer = "V00000000000000000002";
        assertThat(longVer).hasSize(21);
        assertThat(CatalogDownloadVersionSupport.normalizeForPlatformRequest(longVer))
            .isEqualTo(longVer);
    }

    @Test
    void normalizeForPlatformRequest_blankDefaultsToZero() {
        assertThat(CatalogDownloadVersionSupport.normalizeForPlatformRequest(null))
            .isEqualTo("0");
        assertThat(CatalogDownloadVersionSupport.normalizeForPlatformRequest("  "))
            .isEqualTo("0");
    }

    @Test
    void normalizeForCursor_allowsUpTo64() {
        String text = "V".repeat(70);
        assertThat(CatalogDownloadVersionSupport.normalizeForCursor(text)).hasSize(64);
    }
}
