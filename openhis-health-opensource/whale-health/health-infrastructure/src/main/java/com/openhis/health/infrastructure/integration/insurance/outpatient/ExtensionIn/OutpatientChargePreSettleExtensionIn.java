/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 门诊收费第三方预结算命令（Extension → chs-jilin Client 2203→2204→2206）。 */
@Data
@Accessors(chain = true)
public class OutpatientChargePreSettleExtensionIn {

    /** 就诊 {@code adm_encounter.id}。 */
    private Long encounterId;
    /** 本批次待结算收费项 id 列表。 */
    private List<Long> chargeItemIds;
    /** 费用性质 {@code fin_contract.id}。 */
    private Long contractId;
    /** 收银草稿 {@code fin_payment_draft.id}。 */
    private Long paymentDraftId;
    /** 就诊凭证类型（→ mdtrt_cert_type）。 */
    private String payerCertType;
    /** 就诊凭证号码（→ mdtrt_cert_no）。 */
    private String payerCertNo;
    /** 人员编号（→ psn_no）。 */
    private String insuredPersonNo;
    /** 参保地医保区划。 */
    private String insuplcAdmdvs;
    /** 险种类型 insutype。 */
    private String insutype;
    /** 医疗类别 med_type（11 普通门诊 / 14 慢病等）。 */
    private String medType;
    /** 是否使用个人账户 acct_used_flag（1/0）。 */
    private String acctUsedFlag;
}
