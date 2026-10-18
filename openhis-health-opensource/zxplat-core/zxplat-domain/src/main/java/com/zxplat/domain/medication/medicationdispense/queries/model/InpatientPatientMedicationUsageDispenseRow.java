/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.queries.model;

import com.zxplat.domain.share.enums.DispenseStatus;
import com.zxplat.domain.share.enums.MedicationDispenseType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 住院患者药品使用发放行读模型（AS-IP-PHA-005）。
 *
 * <p>仅承载本聚合事实；账单单价/金额与三态展示文案由应用层装载。</p>
 */
@Getter
@Setter
public class InpatientPatientMedicationUsageDispenseRow {

    /** 发放行主键 {@code med_medication_dispense.id}。 */
    private Long dispenseId;

    /** 药品定义主键 {@code medication_id}。 */
    private Long medicationId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 关联药品申请主键 {@code med_req_id}（定价锚点可选）。 */
    private Long medReqId;

    /** 发放状态枚举。 */
    private DispenseStatus statusEnum;

    /** 发放类型枚举（住院/临时/退药等）。 */
    private MedicationDispenseType dispenseEnum;

    /**
     * 数量取值：优先实发 {@code dispense_quantity}，为空则回落开立/占用量 {@code quantity}。
     */
    private BigDecimal quantityValue;

    /** 计量单位编码 {@code unit_code}。 */
    private String quantityUnitCode;

    /** 计划发药时刻（筛选与排序轴）。 */
    private LocalDateTime plannedDispenseTime;

    /**
     * 关联执行记录主键 {@code procedure_id}；可空。
     *
     * <p>有值时可挂接 Procedure.recorded_time 作为客户可见执行时刻（展示轴；过滤仍用 planned）。</p>
     */
    private Long procedureId;

    /** 汇总发药单号；可空，仅展示、不参与本查询分桶。 */
    private String summaryNo;

    /** 批号；可空。 */
    private String lotNumber;

    /** 发药库位主键；本期 AS 不按库房收窄，仍回传供后续扩展。 */
    private Long locationId;
}
