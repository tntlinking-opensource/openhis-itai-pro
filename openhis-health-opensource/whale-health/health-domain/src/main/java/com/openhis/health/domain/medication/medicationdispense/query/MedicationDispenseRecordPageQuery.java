/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.query;

import com.openhis.health.domain.share.constants.PharmacyDispenseRecordExportConstants;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 发退药记录分页查询入参（AS-OP-PHA-004 / AS-IP-PHA-004）。
 *
 * <p>跨聚合关键字、项目类型、就诊场景由应用层预解析后写入 resolved 字段；本聚合 QueryService 仅 IN 收窄。</p>
 */
@Getter
@Setter
public class MedicationDispenseRecordPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 记录类型：{@link com.openhis.health.domain.share.constants.PharmacyDispenseRecordConstants#RECORD_TYPE_ALL}
     * / {@code DISPENSE} / {@code REFUND}；空则视为 {@code ALL}。
     */
    private String recordTypeCode;

    /** {@code true} 时正向行仅 {@code dispense_enum ∈ {OUTPATIENT,TEMPORARY}}（与住院筛互斥，由应用层保证）。 */
    private Boolean outpatientMedicationOnly;

    /** {@code true} 时正向行仅 {@code dispense_enum ∈ {INPATIENT,TEMPORARY}}。 */
    private Boolean inpatientMedicationOnly;

    /** 发药库房（库位）主键；{@code null} 表示不按库房过滤。 */
    private Long locationId;

    /** 发药人（核发药师）{@code practitioner_id}；{@code null} 表示不按发药人过滤。 */
    private Long practitionerId;

    /**
     * 就诊场景预解析主键集合（门诊/住院就诊范围）；非 {@code null} 时 {@code encounter_id IN}。
     * <p>退药行 {@code dispense_enum=REFUND} 亦须落在同场景就诊内：有患者关键字时由应用层写入；
     * 未写入且已设 {@code inpatientMedicationOnly}/{@code outpatientMedicationOnly} 时，
     * QueryService 以 EXISTS 关联 {@code adm_encounter.class_enum}（IMP/AMB）收窄。</p>
     */
    private List<Long> resolvedEncounterIdsForScene;

    /**
     * 药品关键字预解析主键（{@code med_medication_definition.id}）；非 {@code null} 时以 IN 收窄，空列表表示无命中。
     */
    private List<Long> resolvedMedicationIdsForDrugKeyword;

    /**
     * 患者关键字预解析就诊主键；非 {@code null} 时以 {@code encounter_id IN} 收窄，空列表表示无命中。
     */
    private List<Long> resolvedEncounterIdsForPatientKeyword;

    /**
     * 项目类型（{@code medication_category_code}）预解析药品主键；非 {@code null} 时以 IN 收窄，空列表表示无命中。
     */
    private List<Long> resolvedMedicationIdsForCategory;

    /**
     * 开立科室预解析药品申请主键（药嘱 {@code org_id}）；非 null 时 {@code med_req_id IN}；空列表拒查。
     */
    private List<Long> resolvedMedReqIdsForOrderingDepartment;

    /**
     * 发退药记录读侧页大小：默认 10，上限 {@link PharmacyDispenseRecordExportConstants#EXPORT_MAX_ROWS}（与导出行上限一致）。
     *
     * @return 有效页大小
     */
    @Override
    public int getBoundedPageSize() {
        Integer pageSizeValue = getPageSize();
        if (pageSizeValue == null || pageSizeValue < 1) {
            return 10;
        }
        int exportCap = PharmacyDispenseRecordExportConstants.EXPORT_MAX_ROWS;
        if (pageSizeValue > exportCap) {
            return exportCap;
        }
        return pageSizeValue;
    }
}
