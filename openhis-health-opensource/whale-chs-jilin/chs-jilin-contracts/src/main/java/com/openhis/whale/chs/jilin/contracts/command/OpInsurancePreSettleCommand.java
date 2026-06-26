/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 门诊医保预结算命令（health → adapter Port）。 */
@Data
@Accessors(chain = true)
public class OpInsurancePreSettleCommand {

    /** 就诊 {@code adm_encounter.id}。 */
    private Long encounterId;
    /** 本批次待结算收费项 id 列表。 */
    private List<Long> chargeItemIds;
    /** 费用性质 {@code fin_contract.id}（医保账户关联）。 */
    private Long contractId;
    /** 收银草稿 {@code fin_payment_draft.id}。 */
    private Long paymentDraftId;
    /** 就诊凭证类型（mdtrt_cert_type）。 */
    private String mdtrtCertType;
    /** 就诊凭证号码。 */
    private String mdtrtCertNo;
    /** 人员编号（1101 psn_no）。 */
    private String psnNo;
    /** 参保地医保区划。 */
    private String insuplcAdmdvs;
    /** 险种类型。 */
    private String insutype;
    /** 医疗类别（11 普通门诊 / 14 慢病等）。 */
    private String medType;
    /** 是否使用个人账户 acct_used_flag（1/0）。 */
    private String acctUsedFlag;
}
