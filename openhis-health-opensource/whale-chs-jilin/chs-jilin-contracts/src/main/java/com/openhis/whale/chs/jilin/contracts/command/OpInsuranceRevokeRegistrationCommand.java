/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【2202】门诊挂号撤销命令。 */
@Data
@Accessors(chain = true)
public class OpInsuranceRevokeRegistrationCommand {

    private Long encounterId;
    private Long contractId;
    private String medType;
    private String insuplcAdmdvs;
}
