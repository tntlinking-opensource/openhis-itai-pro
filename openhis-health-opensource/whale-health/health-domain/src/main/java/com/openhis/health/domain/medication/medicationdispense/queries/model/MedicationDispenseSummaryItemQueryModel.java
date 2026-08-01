/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.queries.model;

import com.openhis.health.domain.share.enums.DispenseStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 汇总单号维度发放明细查询模型（对应 {@code listMedicationDispensesBySummaryNo}）。
 *
 * <p>供住院汇总发药核对、核发扣库与明细展示；字段与 {@code med_medication_dispense} 主表口径一致。</p>
 */
@Getter
@Setter
public class MedicationDispenseSummaryItemQueryModel {
    /** 汇总单号。 */
    private String summaryNo;
    /** 发放行主键。 */
    private Long id;
    /** 发放状态。 */
    private DispenseStatus statusEnum;
    /** 待发/应发数量。 */
    private BigDecimal quantity;
    /** 药品定义标识。 */
    private Long medicationId;
    /** 发放药房/库位标识。 */
    private Long locationId;
    /** 批号。 */
    private String lotNumber;
    /** 数量单位 code。 */
    private String unitCode;
    /** 就诊标识。 */
    private Long encounterId;
    /** 患者标识。 */
    private Long patientId;
    /** 药品申请（医嘱）标识；摆药单按患者+本字段合并展示。 */
    private Long medReqId;
    /** 用药说明（剂量展示回落）。 */
    private String dosageInstruction;
    /** 用法 code。 */
    private String methodCode;
    /** 频次 code。 */
    private String frequencyCode;
    /** 单次剂量（摆药展示优先；空则回落医嘱）。 */
    private BigDecimal dose;
    /** 剂量单位 code。 */
    private String doseUnitCode;
    /** 追溯码串（可为空）。 */
    private String traceNo;
}
