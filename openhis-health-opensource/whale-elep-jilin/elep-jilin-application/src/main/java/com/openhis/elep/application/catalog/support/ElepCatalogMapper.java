/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.application.catalog.support;

import com.openhis.elep.domain.catalog.dto.ElepCatalogItemDto;
import com.openhis.elep.domain.catalog.model.ElepNationalCatalogRow;

public final class ElepCatalogMapper {

    private ElepCatalogMapper() {
    }

    public static ElepCatalogItemDto toDto(ElepNationalCatalogRow row) {
        if (row == null) {
            return null;
        }
        return new ElepCatalogItemDto()
                .setHilistCode(row.getHilistCode())
                .setHilistName(row.getHilistName())
                .setSpec(row.getSpec())
                .setDosform(row.getDosform())
                .setAprvno(row.getAprvno())
                .setPinyin(row.getPinyin())
                .setValiFlag(row.getValiFlag())
                .setSourceInfno(row.getSourceInfno());
    }
}
