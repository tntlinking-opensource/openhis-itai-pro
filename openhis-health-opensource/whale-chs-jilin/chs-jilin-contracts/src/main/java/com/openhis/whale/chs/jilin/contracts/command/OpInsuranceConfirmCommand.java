/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 门诊医保正式结算命令（2207，须在本地落账前调用）。 */
@Data
@Accessors(chain = true)
public class OpInsuranceConfirmCommand {

    /** 收银草稿 id，与预结算快照关联。 */
    private Long paymentDraftId;
    /** 收费批次号；与 {@link OpInsurancePreSettleResult#chrgBchno} 一致。 */
    private String chrgBchno;
    /** 就诊 id（审计/日志用）。 */
    private Long encounterId;
}
