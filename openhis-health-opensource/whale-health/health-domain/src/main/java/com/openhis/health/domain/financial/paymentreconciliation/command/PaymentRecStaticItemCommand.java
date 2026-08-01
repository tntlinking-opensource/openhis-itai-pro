/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 支付费用分类统计快照行命令（写入 {@code fin_payment_rec_static}）。
 */
@Getter
@Setter
public class PaymentRecStaticItemCommand {
    /** 统计方式编码（DM-FIN-002 §4.10；日结用 {@code 1}=fin_type_code）。 */
    private String methodCode;
    /** 分类编码（fin_type_code）。 */
    private String typeCode;
    /** 分类名称（可选）。 */
    private String typeName;
    /** 本笔支付分摊到该分类的金额（正数；退费符号由日结读侧按 payment 方向处理）。 */
    private BigDecimal amount;
    /** 关联收费明细 ID 串（逗号分隔，可选）。 */
    private String chargeItemIds;
}
