/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/** 住院正式结算撤销命令（Extension 2305）。 */
@Data
@Accessors(chain = true)
public class InpatientChargeRevokeSettleExtensionIn {

    /** 结算回执 id。 */
    private Long settlementRecordId;
    /** payment id。 */
    private Long paymentId;
    /** 预结算 id。 */
    private Long preSettleId;
    /** 平台 setl_id。 */
    private String settlementId;
}
