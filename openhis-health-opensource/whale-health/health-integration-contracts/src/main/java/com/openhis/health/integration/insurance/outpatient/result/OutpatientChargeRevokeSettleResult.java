/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.outpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2208 正式结算撤销结果。 */
@Data
@Accessors(chain = true)
public class OutpatientChargeRevokeSettleResult {

    /** 被撤销的结算回执 id。 */
    private Long settlementRecordId;
    /** 原 setl_id。 */
    private String settlementId;
    /** 原 mdtrt_id。 */
    private String encounterPlatformId;
}
