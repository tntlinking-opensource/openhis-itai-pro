/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NationalCatalogPackFieldExtractorTest {

    @Test
    void fromColumns_readsPackIndexes() {
        String[] cols = new String[50];
        cols[41] = "12";
        cols[42] = "盒";
        cols[43] = "粒";
        cols[48] = "粒";
        NationalCatalogPackFieldExtractor.PackFields pack =
            NationalCatalogPackFieldExtractor.fromColumns(cols);
        assertThat(pack.minPacCnt()).isEqualTo("12");
        assertThat(pack.minPacunt()).isEqualTo("盒");
        assertThat(pack.minPrepunt()).isEqualTo("粒");
        assertThat(pack.minPrcunt()).isEqualTo("粒");
    }

    @Test
    void fromRawJson_parsesArrayAndIgnoresObject() {
        String arrayJson = "[\"0\",\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\","
            + "\"10\",\"11\",\"12\",\"13\",\"14\",\"15\",\"16\",\"17\",\"18\",\"19\","
            + "\"20\",\"21\",\"22\",\"23\",\"24\",\"25\",\"26\",\"27\",\"28\",\"29\","
            + "\"30\",\"31\",\"32\",\"33\",\"34\",\"35\",\"36\",\"37\",\"38\",\"39\","
            + "\"40\",\"24\",\"盒\",\"粒\",\"45\",\"46\",\"47\",\"48\",\"片\"]";
        NationalCatalogPackFieldExtractor.PackFields pack =
            NationalCatalogPackFieldExtractor.fromRawJson(arrayJson);
        assertThat(pack.minPacCnt()).isEqualTo("24");
        assertThat(pack.minPacunt()).isEqualTo("盒");
        assertThat(pack.minPrepunt()).isEqualTo("粒");
        assertThat(pack.minPrcunt()).isEqualTo("片");

        assertThat(NationalCatalogPackFieldExtractor.fromRawJson("{\"hilist_code\":\"X\"}"))
            .isEqualTo(NationalCatalogPackFieldExtractor.EMPTY);
    }
}
