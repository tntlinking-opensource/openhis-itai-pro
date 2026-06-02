/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
