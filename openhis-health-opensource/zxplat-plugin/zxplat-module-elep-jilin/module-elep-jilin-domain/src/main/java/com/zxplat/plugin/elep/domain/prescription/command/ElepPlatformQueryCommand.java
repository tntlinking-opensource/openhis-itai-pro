/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/** 【A0005】平台查询命令（Bridge）。 */
@Data
@Accessors(chain = true)
public class ElepPlatformQueryCommand {

    private String contractBusNo;
    private Long prescriptionId;
    private Long contractId;
    private String hiRxno;
    /** 可选：覆盖平台 ElepQuerPrescriptionInput。 */
    private Map<String, Object> platformBody;
    /** 是否刷新本地 rx_stas 等字段，默认 true。 */
    private Boolean refreshLocal;
}
