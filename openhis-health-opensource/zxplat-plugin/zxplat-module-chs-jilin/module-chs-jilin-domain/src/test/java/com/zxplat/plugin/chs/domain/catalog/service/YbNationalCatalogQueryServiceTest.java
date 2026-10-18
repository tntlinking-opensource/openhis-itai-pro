/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YbNationalCatalogQueryServiceTest {

    @Test
    void prefers1301Detail_whenAprvnoOrSpecPresent() {
        assertThat(YbNationalCatalogQueryService.prefers1301Detail("国药准字", null)).isTrue();
        assertThat(YbNationalCatalogQueryService.prefers1301Detail(null, "0.25g")).isTrue();
        assertThat(YbNationalCatalogQueryService.prefers1301Detail(null, null)).isFalse();
        assertThat(YbNationalCatalogQueryService.prefers1301Detail("  ", "")).isFalse();
    }
}
