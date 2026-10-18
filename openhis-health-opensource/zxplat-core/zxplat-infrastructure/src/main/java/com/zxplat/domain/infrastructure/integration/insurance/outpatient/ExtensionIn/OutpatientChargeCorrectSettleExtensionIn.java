/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2601 冲正命令（2208 失败时 Compensation 降级调用）。 */
@Data
@Accessors(chain = true)
public class OutpatientChargeCorrectSettleExtensionIn {

    /** 结算回执 id（反查 omsgid）。 */
    private Long settlementRecordId;
    /** 原交易 infno；缺省 2207。 */
    private String originalInfno;
}
