/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.catalog.mapper;

import com.openhis.chs.domain.catalog.support.CatalogRowFieldNormalizer;
import org.apache.commons.lang3.StringUtils;

/** 国家目录 chrgitm_lv（01/02/03）与院内药品 Integer 等级互转。 */
public final class MedicationCatalogChrgitmLvMapper {

    private MedicationCatalogChrgitmLvMapper() {
    }

    public static Integer toMedicationLevel(String catalogChrgitmLv) {
        String code = CatalogRowFieldNormalizer.normalizeChrgitmLv(catalogChrgitmLv);
        if (StringUtils.isBlank(code)) {
            return null;
        }
        return switch (code) {
            case "01" -> 1;
            case "02" -> 2;
            case "03" -> 3;
            default -> null;
        };
    }

    public static String toCatalogLevel(Integer medicationChrgitmLv) {
        if (medicationChrgitmLv == null) {
            return null;
        }
        return switch (medicationChrgitmLv) {
            case 1 -> "01";
            case 2 -> "02";
            case 3 -> "03";
            default -> null;
        };
    }
}
