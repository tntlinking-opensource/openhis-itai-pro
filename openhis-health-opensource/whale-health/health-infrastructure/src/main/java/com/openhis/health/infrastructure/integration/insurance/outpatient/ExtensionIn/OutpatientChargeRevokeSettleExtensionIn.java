/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/** 门诊正式结算撤销命令（退费前 Extension 2208）。 */
@Data
@Accessors(chain = true)
public class OutpatientChargeRevokeSettleExtensionIn {

    /** 本地结算回执 {@code yb_settlement_record.id}（优先）。 */
    private Long settlementRecordId;
    /** 原收银 payment id。 */
    private Long paymentId;
    /** 预结算快照 id。 */
    private Long preSettleId;
    /** 平台 setl_id。 */
    private String settlementId;
}
