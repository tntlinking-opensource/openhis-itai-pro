/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.paymentreconciliation.queries.model;

import lombok.Getter;
import lombok.Setter;
import com.openhis.health.domain.share.enums.PaymentStatus;

/**
 * 退费存在性检查结果：是否已存在关联退费单及该单状态。
 */
@Getter
@Setter
public class RefundExistenceCheckResult {
    /** 是否已存在退费记录。 */
    private Boolean exists;
    /** 已存在的退费支付单主键。 */
    private Long refundPaymentId;
    /** 退费支付单当前状态。 */
    private PaymentStatus refundStatusEnum;
}
