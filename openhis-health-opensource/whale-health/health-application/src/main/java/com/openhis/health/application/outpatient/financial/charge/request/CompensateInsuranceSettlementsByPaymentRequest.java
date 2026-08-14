/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.request;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 按支付草稿冲正已确认医保结算（2208/2601）。
 */
@Data
@Accessors(chain = true)
public class CompensateInsuranceSettlementsByPaymentRequest {

    /** 支付草稿主键（与 yb_settlement_record.payment_id 一致）。 */
    private Long paymentDraftId;
}
