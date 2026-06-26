/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.outpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 支付草稿下 ACTIVE 预结算批次快照（confirm 前解析 chrg_bchno）。 */
@Data
@Accessors(chain = true)
public class OutpatientChargePreSettleSnapshot {

    /** 收费批次号 chrg_bchno。 */
    private String chargeBatchNo;

    /** 人员编号 psn_no（刷新预结算时复用）。 */
    private String insuredPersonNo;

    /** 参保地医保区划。 */
    private String insuplcAdmdvs;

    /** 险种类型。 */
    private String insutype;

    /** 就诊凭证类型。 */
    private String payerCertType;

    /** 是否使用个账 acct_used_flag（1/0）。 */
    private String acctUsedFlag;
}
