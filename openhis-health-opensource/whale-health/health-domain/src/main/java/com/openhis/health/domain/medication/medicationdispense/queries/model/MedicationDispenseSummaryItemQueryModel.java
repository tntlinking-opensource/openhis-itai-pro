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
    /** 追溯码串（可为空）。 */
    private String traceNo;
}
