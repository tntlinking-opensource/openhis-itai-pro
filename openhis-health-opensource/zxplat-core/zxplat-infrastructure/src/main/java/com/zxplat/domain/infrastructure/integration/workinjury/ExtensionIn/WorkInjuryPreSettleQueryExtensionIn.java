/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.workinjury.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/** 按支付草稿查询工伤有效预结算快照。 */
@Data
@Accessors(chain = true)
public class WorkInjuryPreSettleQueryExtensionIn {

    private Long paymentDraftId;
}
