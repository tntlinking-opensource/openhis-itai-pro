/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.invoice.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 创建草稿发票命令。
 * <p>
 * 基于患者与收款（支付）上下文生成草稿发票，用于后续正式开票或冲红流程。
 */
@Getter
@Setter
public class CreateDraftInvoiceCommand {
    /** 患者主键。 */
    private Long patientId;
    /** 关联的支付/收款主键。 */
    private Long paymentId;
    /** 发票类型字典编码。 */
    private String typeCode;
    /** 票面金额（收费项合计，结算刻度；含医保已付，非患者现金 displayAmount）。 */
    private BigDecimal amount;
    /** 创建草稿的操作人标识。 */
    private Long operatorId;
}
