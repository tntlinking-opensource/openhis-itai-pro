/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 住院预结算命令（Extension → 2301→2303）。 */
@Data
@Accessors(chain = true)
public class InpatientChargePreSettleCommand {

    /** 就诊 id。 */
    private Long encounterId;
    /** 待结算 chargeItem id 列表。 */
    private List<Long> chargeItemIds = new ArrayList<>();
    /** 费用性质 id。 */
    private Long contractId;
    /** 收银草稿 id。 */
    private Long paymentDraftId;
    /** 就诊凭证类型。 */
    private String payerCertType;
    /** 就诊凭证号码。 */
    private String payerCertNo;
    /** 人员编号 psn_no。 */
    private String insuredPersonNo;
    /** 参保地医保区划。 */
    private String insuplcAdmdvs;
    /** 险种类型。 */
    private String insutype;
    /** 医疗类别（默认 21 普通住院）。 */
    private String medType;
    /** 结算模式：DISCHARGE_FULL 出院全结 / MID_SETTLE 中途结算。 */
    private String settleMode;
    /** 是否使用个人账户 acct_used_flag。 */
    private String acctUsedFlag;
    /** 个人结算方式 psn_setlway。 */
    private String psnSetlway;
}
