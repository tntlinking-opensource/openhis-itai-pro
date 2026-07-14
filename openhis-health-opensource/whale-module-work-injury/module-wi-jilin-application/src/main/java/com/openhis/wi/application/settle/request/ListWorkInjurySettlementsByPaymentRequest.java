/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.application.settle.request;

import lombok.Data;
import lombok.experimental.Accessors;

/** 按 payment 查询工伤结算回执。 */
@Data
@Accessors(chain = true)
public class ListWorkInjurySettlementsByPaymentRequest {

    private Long paymentId;
}
