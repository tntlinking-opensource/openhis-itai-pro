/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【2401】住院入院登记命令 */
@Data
@Accessors(chain = true)
public class IpAdmissionCommand {

    private Long encounterId;
    private Long contractId;
    private String mdtrtCertType;
    private String mdtrtCertNo;
    private String psnNo;
    private String insuplcAdmdvs;
    private String insutype;
    private String medType;

    /**
     * 为 true 且调用方未带就诊凭证时，用患者身份证作为 2403 就诊凭证（登记窗口更正入院科室，不再次读卡）。
     */
    private Boolean idCardAsMdtrtCert;
}
