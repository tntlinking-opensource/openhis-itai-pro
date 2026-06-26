/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.outpatient.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2001 待遇检查命令。 */
@Data
@Accessors(chain = true)
public class YbTreatmentCheckCommand {

    private Long contractId;
    private String contractBusNo;
    private String psnNo;
    private String insutype;
    private String insuplcAdmdvs;
    private String medType;
    private Long operatorId;
    private Long tenantId;
}
