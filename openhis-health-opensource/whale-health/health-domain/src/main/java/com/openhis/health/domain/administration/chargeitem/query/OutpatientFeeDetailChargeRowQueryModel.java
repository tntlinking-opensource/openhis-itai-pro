/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.administration.chargeitem.query;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentContextQueryModel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 门诊费用明细候选行（DM-ADM-011 §6.2.25；ChargeItem 事实 + payment 排序上下文）。
 */
@Getter
@RequiredArgsConstructor
public class OutpatientFeeDetailChargeRowQueryModel {

    /** 收费明细事实。 */
    private final ChargeItem chargeItem;

    /** 关联 payment 上下文（含 billDate 供排序）。 */
    private final OutpatientFeeDetailPaymentContextQueryModel paymentContext;
}
