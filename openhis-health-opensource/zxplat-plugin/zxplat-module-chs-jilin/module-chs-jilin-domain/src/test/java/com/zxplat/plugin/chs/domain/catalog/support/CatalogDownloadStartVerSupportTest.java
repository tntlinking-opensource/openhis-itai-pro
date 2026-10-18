/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogDownloadStartVerSupportTest {

    @Test
    void canonical1305Branch_defaultsToF002() {
        assertThat(CatalogDownloadStartVerSupport.canonical1305Branch(null)).isEqualTo("F002");
        assertThat(CatalogDownloadStartVerSupport.canonical1305Branch("")).isEqualTo("F002");
        assertThat(CatalogDownloadStartVerSupport.canonical1305Branch("f003")).isEqualTo("F003");
    }

    @Test
    void matches1305Branch_prefixOnly() {
        assertThat(CatalogDownloadStartVerSupport.matches1305Branch("F002", "F002")).isTrue();
        assertThat(CatalogDownloadStartVerSupport.matches1305Branch("F002_20250121000003_A", "F002"))
            .isTrue();
        assertThat(CatalogDownloadStartVerSupport.matches1305Branch("F003_20250121000001_A", "F002"))
            .isFalse();
        assertThat(CatalogDownloadStartVerSupport.matches1305Branch("F003_20250121000001_A", "F003"))
            .isTrue();
    }

    @Test
    void resolveStartVer_1305UsesBranchCursorOrInitial() {
        assertThat(CatalogDownloadStartVerSupport.resolveStartVer(
            "1305", "F003", false, null, null)).isEqualTo("F003");

        assertThat(CatalogDownloadStartVerSupport.resolveStartVer(
            "1305", "F003", false, null, "F003_20240101000001_A"))
            .isEqualTo("F003_20240101000001_A");

        assertThat(CatalogDownloadStartVerSupport.resolveStartVer(
            "1305", "F003", true, null, "F003_20240101000001_A"))
            .isEqualTo("F003");
    }
}
