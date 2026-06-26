/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.outpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 门诊挂号平台登记命令（Extension → 2201）。 */
@Data
@Accessors(chain = true)
public class OutpatientRegistrationPlatformCommand {

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
    /** 医疗类别；缺省 11 普通门诊。 */
    private String medType;
}
