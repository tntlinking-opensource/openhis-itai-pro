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

package com.openhis.health.module.querysupport.pharmacy;

import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 已确认退药收口的正向药品医嘱主键预解析（替代发放域 EM 跨根 EXISTS）。
 */
@Component
@RequiredArgsConstructor
public class MedicationDispenseRefundSupersedeLoader {

    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final MedicationRequestQueryService medicationRequestQueryService;

    /**
     * 列出已被确认退药收口的正向药品医嘱主键集合。
     *
     * @return 正向 {@code medReqId} 集合；无命中时返回空列表
     */
    public List<Long> listSupersededForwardMedReqIds() {
        List<Long> refundDispenseMedReqIds = medicationDispenseQueryService.listRefundedRefundDispenseMedReqIds();
        if (refundDispenseMedReqIds.isEmpty()) {
            return List.of();
        }
        List<MedicationRequest> refundRequests =
            medicationRequestQueryService.listByIds(refundDispenseMedReqIds);
        Set<Long> superseded = new LinkedHashSet<>();
        for (MedicationRequest refundReq : refundRequests) {
            if (refundReq == null || Boolean.TRUE.equals(refundReq.getIsDeleted())) {
                continue;
            }
            if (refundReq.getRefundMedicineId() != null) {
                superseded.add(refundReq.getRefundMedicineId());
            }
        }
        return new ArrayList<>(superseded);
    }
}
