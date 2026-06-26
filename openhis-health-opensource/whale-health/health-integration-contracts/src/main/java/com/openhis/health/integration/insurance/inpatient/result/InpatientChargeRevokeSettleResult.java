/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2305 住院正式结算撤销结果。 */
@Data
@Accessors(chain = true)
public class InpatientChargeRevokeSettleResult {

    /** 被撤销的结算回执 id。 */
    private Long settlementRecordId;
    /** 原 setl_id。 */
    private String settlementId;
    /** 原 mdtrt_id。 */
    private String encounterPlatformId;
}
