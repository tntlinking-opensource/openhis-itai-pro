/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊挂号平台登记/撤销命令（Extension → 2201 / 2202）。
 *
 * <p>挂号费路径 {@code medType} 须为 {@code 12}；缺省时实现侧常落 11，易误撤诊疗 map。</p>
 */
@Data
@Accessors(chain = true)
public class OutpatientRegistrationPlatformExtensionIn {

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
    /**
     * 医疗类别（map 分组键）。挂号作废/退号须传 {@code 12}；缺省勿依赖。
     */
    private String medType;
}
