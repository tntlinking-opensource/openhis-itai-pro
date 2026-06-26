/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog;

import com.openhis.chs.api.ChsInfNo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogDownloadPhase1ServiceTest {

    @Test
    void shouldStop_whenNextVersionBlank() {
        assertThat(CatalogDownloadPhase1Service.shouldStopAfterPackage("1307", "0", null))
            .isTrue();
        assertThat(CatalogDownloadPhase1Service.shouldStopAfterPackage("1307", "0", "  "))
            .isTrue();
    }

    @Test
    void shouldStop_whenNextVersionEqualsRequest_like1307Stall() {
        assertThat(CatalogDownloadPhase1Service.shouldStopAfterPackage(
            ChsInfNo.DISEASE_CATALOG_DOWNLOAD, "20240101001", "20240101001"))
            .isTrue();
        assertThat(CatalogDownloadPhase1Service.shouldStopAfterPackage("1307", "0", "0"))
            .isTrue();
    }

    @Test
    void shouldContinue_whenNextVersionAdvances() {
        assertThat(CatalogDownloadPhase1Service.shouldStopAfterPackage("1307", "0", "J001_20230526000000_A"))
            .isFalse();
        assertThat(CatalogDownloadPhase1Service.shouldStopAfterPackage("1301", "0", "V00000000000000000002"))
            .isFalse();
    }

}
