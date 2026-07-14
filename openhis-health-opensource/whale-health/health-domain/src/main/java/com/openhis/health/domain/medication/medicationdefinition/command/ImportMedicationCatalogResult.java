/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdefinition.command;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 药品目录导入结果。
 */
@Getter
@AllArgsConstructor
/**
 * ImportMedicationCatalogResult。
 */
public class ImportMedicationCatalogResult {
    /** 导入成功条数。 */
    /**
     * successCount。
     */
    private Integer successCount;
    /** 导入失败条数。 */
    /**
     * failCount。
     */
    private Integer failCount;
}
