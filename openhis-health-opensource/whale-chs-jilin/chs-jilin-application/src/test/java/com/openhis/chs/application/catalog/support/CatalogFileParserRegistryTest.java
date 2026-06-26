/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.support;

import com.openhis.chs.api.ChsInfNo;
import com.openhis.chs.domain.catalog.ChsCatalogConstants;
import com.openhis.chs.domain.catalog.support.CatalogDownloadStartVerSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogFileParserRegistryTest {

    @Test
    void registry_containsAllOpenHisInfno() {
        assertThat(CatalogFileParserRegistry.supportedInfno())
            .contains(
                ChsInfNo.DRUG_CATALOG_DOWNLOAD,
                ChsInfNo.HERB_CATALOG_DOWNLOAD,
                ChsInfNo.MEDICAL_SERVICE_CATALOG_DOWNLOAD,
                ChsInfNo.MEDICAL_CONSUMABLE_CATALOG_DOWNLOAD,
                ChsInfNo.DISEASE_CATALOG_DOWNLOAD,
                ChsInfNo.SURGERY_CATALOG_DOWNLOAD,
                ChsInfNo.SPECIAL_DISEASE_CATALOG_DOWNLOAD,
                ChsInfNo.TCM_DISEASE_CATALOG_DOWNLOAD,
                ChsInfNo.TCM_SYNDROME_CATALOG_DOWNLOAD);
    }

    @Test
    void startVer_defaults_matchPlatformSpec() {
        assertThat(CatalogDownloadStartVerSupport.defaultInitialVer(ChsInfNo.DRUG_CATALOG_DOWNLOAD, null))
            .isEqualTo(ChsCatalogConstants.DEFAULT_CATALOG_DOWNLOAD_VER);
        assertThat(CatalogDownloadStartVerSupport.defaultInitialVer(ChsInfNo.MEDICAL_SERVICE_CATALOG_DOWNLOAD, "F003"))
            .isEqualTo(ChsCatalogConstants.INITIAL_VER_SERVICE_F003);
        assertThat(CatalogDownloadStartVerSupport.defaultInitialVer(ChsInfNo.SURGERY_CATALOG_DOWNLOAD, null))
            .isEqualTo(ChsCatalogConstants.INITIAL_VER_SURGERY);
    }
}
