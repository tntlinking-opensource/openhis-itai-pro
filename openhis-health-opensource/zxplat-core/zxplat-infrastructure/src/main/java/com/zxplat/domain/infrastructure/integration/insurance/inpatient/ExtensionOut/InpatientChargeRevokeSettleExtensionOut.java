/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2305 住院正式结算撤销结果。 */
@Data
@Accessors(chain = true)
public class InpatientChargeRevokeSettleExtensionOut {

    /** 被撤销的结算回执 id。 */
    private Long settlementRecordId;
    /** 原 setl_id。 */
    private String settlementId;
    /** 原 mdtrt_id。 */
    private String encounterPlatformId;
}
