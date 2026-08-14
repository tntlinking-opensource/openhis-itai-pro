/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 将已确认门诊结算回执改挂到新的支付草稿（银联补收 / 孤儿 2207 收口）。
 */
@Data
@Accessors(chain = true)
public class OutpatientChargeRebindSettlementPaymentExtensionIn {

    /** 待改挂的结算回执主键列表。 */
    private List<Long> settlementRecordIds;

    /** 新支付草稿主键。 */
    private Long newPaymentId;
}
