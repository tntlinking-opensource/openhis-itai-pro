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

package com.openhis.health.application.querysupport.pharmacy;

import com.openhis.health.domain.medication.medicationdispense.query.InpatientPharmacyPendingEncounterPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.OutpatientPharmacyPendingEncounterPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.PageReturnPatientQuery;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * 药房发药/退药患者队列：委托 {@link MedicationDispenseQueryService} 完成跨聚合分页读组装（B.6.1）。
 *
 * <p>分页键 {@code pageNo} 为 1-based，Criteria 分组分页在 {@link MedicationDispenseQueryService}（B.10.11）。</p>
 */
@Component
@RequiredArgsConstructor
public class PharmacyEncounterQueueLoader {

    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /**
     * 门诊发药队列：分页返回存在门诊药品发放事实的就诊主键。
     *
     * @param query 库房、发放状态、关键字等；{@code null} 时按空条件
     * @return 就诊主键页（按发放创建时间倒序）
     */
    public Page<Long> pageEncounterIdsForOutpatientPharmacyPending(OutpatientPharmacyPendingEncounterPageQuery query) {
        return this.medicationDispenseQueryService.pageEncounterIdsForOutpatientPharmacyPending(query);
    }

    /**
     * 住院发药队列：分页返回存在药品发放事实且住院在院的就诊主键。
     *
     * @param query 库房、病区、关键字等
     * @return 就诊主键页
     */
    public Page<Long> pageEncounterIdsForInpatientPharmacyPending(InpatientPharmacyPendingEncounterPageQuery query) {
        return this.medicationDispenseQueryService.pageEncounterIdsForInpatientPharmacyPending(query);
    }

    /**
     * 门诊药房退药患者队列：分页返回存在待退药或已退药药品发放事实的就诊主键。
     *
     * @param query 库房、时段、科室、关键字等
     * @return 就诊主键页（按退药状态变更时间倒序；单页上限 100）
     */
    public Page<Long> pageEncounterIdsForOutpatientPharmacyReturn(PageReturnPatientQuery query) {
        return this.medicationDispenseQueryService.pageEncounterIdsForOutpatientPharmacyReturn(query);
    }
}
