/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2202 门诊挂号撤销命令 */
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
