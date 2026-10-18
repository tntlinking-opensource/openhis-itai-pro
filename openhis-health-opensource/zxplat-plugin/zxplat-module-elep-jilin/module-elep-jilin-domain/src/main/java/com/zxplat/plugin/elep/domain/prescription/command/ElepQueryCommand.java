/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 电子处方查询 Client 命令。 */
@Data
@Accessors(chain = true)
public class ElepQueryCommand {

    private Long prescriptionId;
    private Long contractId;
    private String contractBusNo;
    private String hiRxno;
    private java.util.Map<String, Object> platformBody;
    private Boolean refreshLocal;
}
