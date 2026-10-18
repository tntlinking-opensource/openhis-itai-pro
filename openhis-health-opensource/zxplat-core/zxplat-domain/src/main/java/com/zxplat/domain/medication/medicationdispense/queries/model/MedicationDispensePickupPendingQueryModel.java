/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 住院护士站汇总领药 · 待汇总发放明细行（**AS-IP-NUR-010 · IF-IP-SUM-01**）。
 *
 * <p>{@code chargeUnitPrice} 来自与发放行 1:1 锚定的 {@code ChargeItem.unit_price}，只读、不写账单。</p>
 */
@Getter
@Setter
public class MedicationDispensePickupPendingQueryModel {

    /** 发放行主键（可勾选时间点）。 */
    private Long dispenseId;

    /** 执行记录主键（已执行挂接）。 */
    private Long procedureId;

    /** 计划发药时刻。 */
    private LocalDateTime plannedDispenseTime;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 药品定义主键。 */
    private Long medicationId;

    /** 药品申请主键。 */
    private Long medReqId;

    /** 发放药房/库位主键。 */
    private Long locationId;

    /** 发放数量（原单位、原数量，明细 Tab 展示口径）。 */
    private BigDecimal quantity;

    /** 发放数量单位编码。 */
    private String unitCode;

    /** 只读账单单价（ChargeItem.unit_price）。 */
    private BigDecimal chargeUnitPrice;

    /** 用药说明快照。 */
    private String dosageInstruction;

    /** 用法编码。 */
    private String methodCode;

    /** 频次编码。 */
    private String frequencyCode;
}
