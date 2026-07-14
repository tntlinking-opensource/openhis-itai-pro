/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.paymentreconciliation.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊费用明细：单条 payment 快照与 chargeItem 的读侧关联（DM-FIN-002 §6.2.17；AS-OP-FIN-005 **BR-OP-FDT-012**）。
 *
 * <p>同一 chargeItem 可出现在收费与退费两笔 payment 快照中，须各生成一行以实现收退平账展示，
 * 禁止仅以 chargeItemId 去重覆盖 payment 上下文。</p>
 */
@Getter
@Setter
public class OutpatientFeeDetailChargePaymentLinkQueryModel {

    /** 收费明细主键。 */
    private Long chargeItemId;

    /** 本快照行对应的 payment 读上下文。 */
    private OutpatientFeeDetailPaymentContextQueryModel paymentContext;
}
