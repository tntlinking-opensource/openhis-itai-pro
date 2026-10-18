/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.settlement.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** HIS 收银结算完成后的医保挂接命令 */
@Getter
@Setter
public class OnPaymentSettledCommand {

    /** HIS 支付单 ID */
    private Long paymentId;
    /** 就诊 ID */
    private Long encounterId;
    /** 关联收费项 ID 列表（逗号分隔或 JSON，由 health 侧约定） */
    private String chargeItemIds;
    /** 展示金额（收银界面合计） */
    private BigDecimal displayAmount;
    /** 账单日期 */
    private LocalDateTime billDate;
    /** 录入人/操作员 ID */
    private Long entererId;
    /** 是否住院收银 */
    private boolean inpatient;
    /** 本支付单关联的医保结算凭证行 */
    private List<OnPaymentSettledYbSettlementLine> ybSettlements;
}
