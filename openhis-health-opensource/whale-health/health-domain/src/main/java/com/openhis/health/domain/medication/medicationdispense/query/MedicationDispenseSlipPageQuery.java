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

package com.openhis.health.domain.medication.medicationdispense.query;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 发药单分页查询入参（原 MedicationDispenseSlip 读模型 §3，现查 {@code med_medication_dispense} 主表）。
 */
@Getter
@Setter
public class MedicationDispenseSlipPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 关键字预解析的就诊主键（由 {@code PharmacyDispenseSlipQuerySupport} 写入；非 null 时以 IN 收窄，空列表表示无命中）。
     */
    private List<Long> resolvedEncounterIdsForKeyword;

    /**
     * 医嘱状态预解析的药品申请主键（由应用层按就诊范围写入；非 null 时以 {@code medReqId} IN 收窄）。
     */
    private List<Long> resolvedMedReqIdsForRequestStatus;

    /** 开单科室组织主键（药嘱 {@code org_id}，{@code adm_organization.id}）；由请求显式传入。 */
    private Long orderingDepartmentId;

    /**
     * 开单科室预解析的药品申请主键（非 null 时以 {@code medReqId} IN 收窄）。
     */
    private List<Long> resolvedMedReqIdsForOrderingDepartment;

    /**
     * 已被确认退药收口的正向药品医嘱主键（由 {@code MedicationDispenseRefundSupersedeLoader} 写入）。
     */
    private List<Long> resolvedSupersededForwardMedReqIds;

    private Long locationId;
    private Long encounterId;
    /** 排除的就诊主键（与 {@code patientId} 联用，历史处方 Tab）。 */
    private Long excludeEncounterId;
    private Long patientId;
    private Long medicationId;
    private String statusEnum;
    private List<String> dispenseStatusEnumIn;
    private List<String> requestStatusEnumIn;
    private Boolean outpatientMedicationOnly;
    private Boolean inpatientMedicationOnly;
    private String lotNumber;
    private String summaryNo;
}
