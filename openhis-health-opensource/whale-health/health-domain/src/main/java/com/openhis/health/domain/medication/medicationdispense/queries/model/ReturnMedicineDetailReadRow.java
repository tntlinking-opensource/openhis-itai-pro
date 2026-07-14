/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 门诊药房退药明细行（待退/已退发放事实 + 药嘱装配，供 AS-OP-PHA-002 明细映射）。
 *
 * <p>退药读侧<strong>不</strong>携带原正向发药单主键；应退金额由 App 按 {@code refundOriginRequestId}（{@code service_id}）汇总。</p>
 */
@Getter
@Setter
public class ReturnMedicineDetailReadRow {

    /** 退药关联发药行主键 {@code med_medication_dispense.id}。 */
    private Long dispenseId;
    /** 退药申请/药嘱主键 {@code med_medication_request.id}。 */
    private Long requestId;
    /** 原正向药嘱主键（{@code service_id}，应退金额汇总锚点）。 */
    private Long refundOriginRequestId;
    /** 药品定义主键。 */
    private Long itemId;
    /** 药品名称。 */
    private String itemName;
    /** 批号。 */
    private String lotNumber;
    /** 退药数量（拆零）。 */
    private BigDecimal quantity;
    /** 计量单位编码。 */
    private String unitCode;
    /** 计量单位展示文案。 */
    private String unitCodeText;
    /** 应退/已退金额。 */
    private BigDecimal totalPrice;
    /** 开单医生姓名。 */
    private String doctorName;
    /** 退药状态持久化码。 */
    private String refundEnum;
    /** 退药状态展示文案。 */
    private String refundEnumText;
    /** 药嘱状态持久化码。 */
    private String reqStatus;
    /** 药嘱状态展示文案。 */
    private String reqStatusText;
    /** 关联服务表名（药品/组套等）。 */
    private String serviceTable;
    /** 追溯码。 */
    private String traceNo;
}
