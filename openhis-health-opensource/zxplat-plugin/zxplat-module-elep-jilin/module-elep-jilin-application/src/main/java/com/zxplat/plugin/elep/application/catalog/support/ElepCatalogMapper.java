/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.application.catalog.support;

import com.zxplat.plugin.elep.domain.catalog.model.ElepCatalogItem;
import com.zxplat.plugin.elep.domain.catalog.model.ElepNationalCatalogRow;

public final class ElepCatalogMapper {

    private ElepCatalogMapper() {
    }

    public static ElepCatalogItem toDto(ElepNationalCatalogRow row) {
        if (row == null) {
            return null;
        }
        return new ElepCatalogItem()
                .setHilistCode(row.getHilistCode())
                .setHilistName(row.getHilistName())
                .setSpec(row.getSpec())
                .setDosform(row.getDosform())
                .setAprvno(row.getAprvno())
                .setProdentpName(row.getProdentpName())
                .setPinyin(row.getPinyin())
                .setValiFlag(row.getValiFlag())
                .setSourceInfno(row.getSourceInfno());
    }
}
