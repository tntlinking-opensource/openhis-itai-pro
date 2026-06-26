/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.outpatient.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2202 门诊挂号撤销命令。 */
@Data
@Accessors(chain = true)
public class OpRevokePlatformRegistrationCommand {

    private Long encounterId;
    private Long contractId;
    private String medType;
    private String contractBusNo;
    private String insuplcAdmdvs;
    private Long operatorId;
    private Long tenantId;
}
