/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/** 住院入院登记命令（Extension → 2401）。 */
@Data
@Accessors(chain = true)
public class InpatientChargeAdmissionExtensionIn {

    /** 就诊 id。 */
    private Long encounterId;
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
    /** 医疗类别 med_type。 */
    private String medType;

    /**
     * 为 true 时，2403 在未传就诊凭证的情况下用患者身份证补齐（更正入院科室）。
     */
    private Boolean idCardAsMdtrtCert;
}
