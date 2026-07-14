/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【2201】门诊挂号登记命令（bridge Invoker 出站前组装） */
@Data
@Accessors(chain = true)
public class OpPlatformRegistrationCommand {

    private Long encounterId;
    private Long contractId;
    private String mdtrtCertType;
    private String mdtrtCertNo;
    private String psnNo;
    private String insuplcAdmdvs;
    private String insutype;
    private String medType;
}
