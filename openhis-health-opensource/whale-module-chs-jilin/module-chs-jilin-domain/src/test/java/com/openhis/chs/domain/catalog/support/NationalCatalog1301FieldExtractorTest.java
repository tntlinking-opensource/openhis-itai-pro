/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NationalCatalog1301FieldExtractorTest {

    @Test
    void resolveName_fallsBackFromPlaceholderGenericToRegisteredName() {
        String[] cols = new String[97];
        cols[1] = "无";
        cols[3] = "--";
        cols[7] = "阿酚咖敏片";
        cols[91] = "--";
        cols[95] = "2024-12-27 00:00:00.0";
        cols[96] = "null";

        NationalCatalog1301FieldExtractor.DisplayFields fields =
            NationalCatalog1301FieldExtractor.fromColumns(cols);

        assertThat(fields.hilistName()).isEqualTo("阿酚咖敏片");
        assertThat(fields.chrgitmLv()).isNull();
        assertThat(fields.begndate()).isEqualTo("2024-12-27 00:00:00.0");
        assertThat(fields.enddate()).isNull();
    }

    @Test
    void clean_treatsPlatformPlaceholdersAsEmpty() {
        assertThat(NationalCatalog1301FieldExtractor.clean("null")).isNull();
        assertThat(NationalCatalog1301FieldExtractor.clean("NULL")).isNull();
        assertThat(NationalCatalog1301FieldExtractor.clean("--")).isNull();
        assertThat(NationalCatalog1301FieldExtractor.clean("-")).isNull();
        assertThat(NationalCatalog1301FieldExtractor.clean(" 乙类 ")).isEqualTo("乙类");
    }
}
