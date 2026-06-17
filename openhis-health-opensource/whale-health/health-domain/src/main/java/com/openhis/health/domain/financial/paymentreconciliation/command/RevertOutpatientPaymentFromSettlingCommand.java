/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊医保协同失败回退：支付单 SETTLING→DRAFT 命令。
 */
@Getter
@Setter
public class RevertOutpatientPaymentFromSettlingCommand {

    /** 支付草稿主键。 */
    private Long paymentId;
    /** 操作员主键（审计用）。 */
    private Long operatorId;
}
