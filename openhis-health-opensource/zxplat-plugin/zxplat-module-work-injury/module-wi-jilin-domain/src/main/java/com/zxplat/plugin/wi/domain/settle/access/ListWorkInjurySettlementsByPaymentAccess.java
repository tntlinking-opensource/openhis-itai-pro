/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.settle.access;

import com.zxplat.plugin.wi.domain.settle.result.WorkInjurySettlementRecordSummary;

import java.util.List;

/** 按 payment 查询工伤正式结算回执端口。 */
public interface ListWorkInjurySettlementsByPaymentAccess {

    List<WorkInjurySettlementRecordSummary> listByPayment(Long paymentId);
}
