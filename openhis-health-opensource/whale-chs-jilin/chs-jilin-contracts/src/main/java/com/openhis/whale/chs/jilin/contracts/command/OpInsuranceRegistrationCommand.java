/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【2201】门诊挂号登记命令。 */
@Data
@Accessors(chain = true)
public class OpInsuranceRegistrationCommand {

    /** 就诊 id。 */
    private Long encounterId;
    /** 费用性质 id。 */
    private Long contractId;
    /** 就诊凭证类型。 */
    private String mdtrtCertType;
    /** 就诊凭证号码。 */
    private String mdtrtCertNo;
    /** 人员编号 psn_no。 */
    private String psnNo;
    /** 参保地医保区划。 */
    private String insuplcAdmdvs;
    /** 险种类型。 */
    private String insutype;
    /** 医疗类别；缺省 11 普通门诊。 */
    private String medType;
}
