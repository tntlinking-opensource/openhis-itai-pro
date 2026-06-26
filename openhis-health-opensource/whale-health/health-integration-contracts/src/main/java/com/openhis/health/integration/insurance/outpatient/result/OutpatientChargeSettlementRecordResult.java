/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.outpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 门诊第三方正式结算回执摘要（退费/2601 补偿查询用）。 */
@Data
@Accessors(chain = true)
public class OutpatientChargeSettlementRecordResult {

    /** 结算回执主键。 */
    private Long settlementRecordId;
    /** 关联 payment id。 */
    private Long paymentId;
    /** 关联预结算 id。 */
    private Long preSettleId;
    /** 费用性质 id。 */
    private Long contractId;
    /** 收费批次号 chrg_bchno。 */
    private String chargeBatchNo;
    /** 平台 setl_id。 */
    private String settlementId;
    /** 平台 mdtrt_id。 */
    private String encounterPlatformId;
    /** 2207 原交易 msgid（2601 冲正 omsgid 来源）。 */
    private String msgid2207;
}
