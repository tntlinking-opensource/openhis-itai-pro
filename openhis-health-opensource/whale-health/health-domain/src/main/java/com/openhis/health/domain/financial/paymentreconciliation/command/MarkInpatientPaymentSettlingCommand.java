/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 住院医保协同路径：支付草稿 DRAFT→SETTLING 命令。
 */
@Getter
@Setter
public class MarkInpatientPaymentSettlingCommand {

    /** 支付草稿主键 {@code fin_payment_reconciliation.id}。 */
    private Long paymentId;
    /** 操作员主键。 */
    private Long operatorId;
}
