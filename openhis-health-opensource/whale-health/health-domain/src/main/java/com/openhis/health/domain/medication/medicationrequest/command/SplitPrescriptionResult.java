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

package com.openhis.health.domain.medication.medicationrequest.command;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 药品处分方结果。
 */
@Getter
@AllArgsConstructor
/**
 * SplitPrescriptionResult。
 */
public class SplitPrescriptionResult {
    /** 参与分方医嘱总数。 */
    /**
     * totalCount。
     */
    private Integer totalCount;
    /** 分方后单元数量。 */
    /**
     * splitUnitCount。
     */
    private Integer splitUnitCount;
}
