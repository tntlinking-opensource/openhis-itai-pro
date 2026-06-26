/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 【2301→2303】住院预结算命令。 */
@Data
@Accessors(chain = true)
public class IpInsurancePreSettleCommand {

    /** 就诊 id。 */
    private Long encounterId;
    /** 待结算收费项 id 列表。 */
    private List<Long> chargeItemIds;
    /** 费用性质 id。 */
    private Long contractId;
    /** 收银草稿 id。 */
    private Long paymentDraftId;
    /** 就诊凭证类型。 */
    private String mdtrtCertType;
    /** 就诊凭证号码。 */
    private String mdtrtCertNo;
    /** 人员编号。 */
    private String psnNo;
    /** 参保地医保区划。 */
    private String insuplcAdmdvs;
    /** 险种类型。 */
    private String insutype;
    /** 医疗类别（默认 21 普通住院）。 */
    private String medType;
    /** 结算模式：DISCHARGE_FULL 出院全结 / MID_SETTLE 中途结算。 */
    private String settleMode;
    /** 是否使用个人账户（平台 acct_used_flag）。 */
    private String acctUsedFlag;
    /** 个人结算方式 psn_setlway。 */
    private String psnSetlway;
}
