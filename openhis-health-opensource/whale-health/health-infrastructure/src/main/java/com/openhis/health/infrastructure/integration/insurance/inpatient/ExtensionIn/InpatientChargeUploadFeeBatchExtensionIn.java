/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 住院费用明细单批上传命令（Extension → 2301，最多 100 行）。 */
@Data
@Accessors(chain = true)
public class InpatientChargeUploadFeeBatchExtensionIn {

    /** 就诊 id。 */
    private Long encounterId;
    /** 待结算 chargeItem id 列表。 */
    private List<Long> chargeItemIds = new ArrayList<>();
    /** 费用性质 id。 */
    private Long contractId;
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
    /** 结算模式：DISCHARGE_FULL / MID_SETTLE。 */
    private String settleMode;
    /** 是否使用个人账户。 */
    private String acctUsedFlag;
    /** 个人结算方式。 */
    private String psnSetlway;
    /** 上批返回的平台就诊 ID；首批可空。 */
    private String preferredMdtrtId;
}
