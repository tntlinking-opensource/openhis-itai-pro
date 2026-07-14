/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.query;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentContextQueryModel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 工作量统计候选 chargeItem 行（ChargeItem 实体 + payment 读上下文）。
 *
 * <p>由 {@link com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService#listPractitionerWorkloadChargeRows}
 * 在 payment 预解析 scope 内组装；供 fin_type / 挂号 / 明细分支复用。</p>
 */
@Getter
@RequiredArgsConstructor
public class PractitionerWorkloadChargeRowQueryModel {

    /** 候选收费明细（已过滤 SETTLED/REFUNDED 与 scope）。 */
    private final ChargeItem chargeItem;

    /** 对应 payment 快照上下文（实收分摊由应用层 enrich）。 */
    private final OutpatientFeeDetailPaymentContextQueryModel paymentContext;
}
