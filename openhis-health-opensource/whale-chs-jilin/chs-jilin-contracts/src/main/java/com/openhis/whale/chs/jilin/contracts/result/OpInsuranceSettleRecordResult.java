/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 门诊医保正式结算回执摘要（{@code yb_settlement_record} 只读视图）。 */
@Data
@Accessors(chain = true)
public class OpInsuranceSettleRecordResult {

    /** 结算回执主键。 */
    private Long settlementRecordId;
    /** 关联收银 payment id。 */
    private Long paymentId;
    /** 关联预结算快照 id。 */
    private Long preSettleId;
    /** 费用性质 id。 */
    private Long contractId;
    /** 收费批次号。 */
    private String chrgBchno;
    /** 平台结算 ID。 */
    private String setlId;
    /** 平台就诊 ID。 */
    private String mdtrtId;
    /** 2207 原交易 msgid（2601 冲正 omsgid 来源）。 */
    private String msgid2207;
}
