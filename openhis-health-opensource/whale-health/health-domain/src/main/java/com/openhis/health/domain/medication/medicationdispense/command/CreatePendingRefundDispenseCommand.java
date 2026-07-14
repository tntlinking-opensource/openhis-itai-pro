/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 创建待退药发放单命令。
 * <p>
 * {@code refundRequestId} 写入新行的 {@code med_req_id}：门诊退药申请场景为退药申请主键；
 * 住院取消执行（{@code AS-IP-NUR-012}）为原发放单关联药嘱主键。
 * {@code procedureId} 可选：住院取消执行须传入新 CANCEL 执行记录主键，与正向执行发放单挂 COMPLETED 行对称。
 */
@Getter
@Setter
public class CreatePendingRefundDispenseCommand {
    /** 操作人标识。 */
    private Long operatorId;
    /** 原始发放单标识。 */
    private Long originDispenseId;
    /** 退药请求标识（落库为 {@code med_req_id}）。 */
    private Long refundRequestId;
    /** 退药数量。 */
    private BigDecimal refundQuantity;
    /** 执行记录标识（住院取消执行挂接新 CANCEL 行；门诊退药申请可不传）。 */
    private Long procedureId;
}
