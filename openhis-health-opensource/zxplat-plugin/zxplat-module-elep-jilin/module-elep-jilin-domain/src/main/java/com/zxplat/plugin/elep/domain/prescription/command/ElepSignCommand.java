/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/** A0002 医保电子签名命令。 */
@Data
@Accessors(chain = true)
public class ElepSignCommand {

    private String contractBusNo;
    private Long prescriptionId;
    /** 可选：覆盖自动组装的平台 ElepSignatureInput。 */
    private Map<String, Object> platformBody;
}
