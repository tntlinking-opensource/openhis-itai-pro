/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/** 按支付草稿查 ACTIVE 预结算入参。 */
@Data
@Accessors(chain = true)
public class OutpatientChargePreSettleListExtensionIn {

    private Long paymentDraftId;
}
