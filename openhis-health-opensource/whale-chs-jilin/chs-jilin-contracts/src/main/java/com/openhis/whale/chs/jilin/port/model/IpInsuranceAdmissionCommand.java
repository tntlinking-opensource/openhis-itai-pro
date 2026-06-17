/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 住院医保入院登记（2401）命令。 */
@Data
@Accessors(chain = true)
public class IpInsuranceAdmissionCommand {

    private Long encounterId;
    private Long contractId;
    private String mdtrtCertType;
    private String mdtrtCertNo;
    private String psnNo;
    private String insuplcAdmdvs;
    private String insutype;
    private String medType;
}
