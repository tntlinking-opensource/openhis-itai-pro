/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 门诊混合预结算命令（多 accountId|medType|diseCodg 分组，Extension → Client preSettleBatch）。 */
@Data
@Accessors(chain = true)
public class OutpatientChargePreSettleBatchExtensionIn {

    /** 就诊 id。 */
    private Long encounterId;
    /** 全部待结算 chargeItem id（Client 内部分组）。 */
    private List<Long> chargeItemIds;
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
}
