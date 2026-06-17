/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 住院取消结算收口命令：原结算单与逆向退款单一并推进为 REFUNDED。
 */
@Getter
@Setter
public class CompleteInpatientSettleCancelRefundCommand {

    /** 原住院结算支付主单主键。 */
    private Long originalPaymentId;

    /** 取消结算逆向退款单主键（relation_id 指向原单）。 */
    private Long reversePaymentId;

    /** 医保结算 ID 列表序列化串（可选，反结后回写）。 */
    private String ybSettleIds;

    /** 结果枚举编码（可选）。 */
    private String outcomeEnum;

    /** 账单日期（可选；默认由领域服务取当前时间）。 */
    private LocalDateTime billDate;
}
