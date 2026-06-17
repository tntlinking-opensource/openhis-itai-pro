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

import lombok.Getter;

import java.util.Collections;
import java.util.Map;

/**
 * 药品处方分方结果：按申请主键回写处方号与处方类别码。
 */
@Getter
public class SplitPrescriptionResult {

    /** 参与分方医嘱总数。 */
    private final int totalCount;

    /** 分方后处方单元数量。 */
    private final int splitUnitCount;

    /** 药品申请主键 → 处方号。 */
    private final Map<Long, String> prescriptionNoByRequestId;

    /** 药品申请主键 → 处方类别码（{@code rx_type_code}）。 */
    private final Map<Long, String> rxTypeCodeByRequestId;

    /**
     * 构造分方结果。
     *
     * @param totalCount               参与行数
     * @param splitUnitCount           处方单元数
     * @param prescriptionNoByRequestId 处方号映射
     * @param rxTypeCodeByRequestId    处方类别映射
     */
    public SplitPrescriptionResult(
        int totalCount,
        int splitUnitCount,
        Map<Long, String> prescriptionNoByRequestId,
        Map<Long, String> rxTypeCodeByRequestId
    ) {
        this.totalCount = totalCount;
        this.splitUnitCount = splitUnitCount;
        if (prescriptionNoByRequestId == null) {
            this.prescriptionNoByRequestId = Collections.emptyMap();
        } else {
            this.prescriptionNoByRequestId = Collections.unmodifiableMap(prescriptionNoByRequestId);
        }
        if (rxTypeCodeByRequestId == null) {
            this.rxTypeCodeByRequestId = Collections.emptyMap();
        } else {
            this.rxTypeCodeByRequestId = Collections.unmodifiableMap(rxTypeCodeByRequestId);
        }
    }
}
