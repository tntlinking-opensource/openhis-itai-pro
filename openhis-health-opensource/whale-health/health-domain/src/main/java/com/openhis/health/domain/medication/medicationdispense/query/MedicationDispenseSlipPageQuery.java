/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

    /** 发药窗口/药房位置 {@code adm_location.id}（单库位精确匹配；与 {@link #locationIds} 互斥时优先本字段）。 */
    private Long locationId;

    /**
     * 发药库房库位主键集合（与 IF-OPP-02/IF-IPP-02 队列 {@code location_id IN} 同源）；
     * 仅在 {@link #locationId} 为空时生效。
     */
    private List<Long> locationIds;
    /** 就诊主键（单就诊 Tab 精确过滤）。 */
    private Long encounterId;
    /** 排除的就诊主键（与 {@code patientId} 联用，历史处方 Tab）。 */
    private Long excludeEncounterId;
    /** 患者主键（跨就诊查发药记录）。 */
    private Long patientId;
    /** 药品定义主键 {@code med_medication_definition.id}。 */
    private Long medicationId;
    /** 发药单/发放状态单值筛选。 */
    private String statusEnum;
    /** 发放状态多值 IN 筛选。 */
    private List<String> dispenseStatusEnumIn;
    /** 关联药嘱状态多值 IN 筛选（由预解析 medReqId 收窄）。 */
    private List<String> requestStatusEnumIn;
    /** true 时仅查门诊药嘱关联的发药行。 */
    private Boolean outpatientMedicationOnly;
    /** true 时仅查住院药嘱关联的发药行。 */
    private Boolean inpatientMedicationOnly;
    /** 批号精确匹配。 */
    private String lotNumber;
    /** 汇总发药单号（住院汇总发药场景）。 */
    private String summaryNo;
}
