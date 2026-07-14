/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2404/2405 入出院撤销命令 */
@Data
@Accessors(chain = true)
public class IpEncounterPlatformCommand {

    private Long encounterId;
    private Long contractId;
    private String contractBusNo;
    private String mdtrtId;
    private String psnNo;
    private String insuplcAdmdvs;
    private String medType;
    private Long operatorId;
    private Long tenantId;
}
