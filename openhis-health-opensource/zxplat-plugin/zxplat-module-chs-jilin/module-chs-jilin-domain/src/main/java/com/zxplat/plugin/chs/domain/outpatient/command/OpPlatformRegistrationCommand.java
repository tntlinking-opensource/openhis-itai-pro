/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.outpatient.command;

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

    /**
     * 为 true 时用患者身份证作为 2201 凭证，并要求能取到原挂号参保地，禁止回落医院就医地。
     */
    private Boolean idCardAsMdtrtCert;
}
