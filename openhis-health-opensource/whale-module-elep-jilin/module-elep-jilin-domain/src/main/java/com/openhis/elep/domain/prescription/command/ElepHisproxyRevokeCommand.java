/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/** A0004 撤销命令（Bridge API）。 */
@Data
@Accessors(chain = true)
public class ElepHisproxyRevokeCommand {

    private String contractBusNo;
    private Long prescriptionId;
    /** 平台 ElepRevokeInput JSON 结构。 */
    private Map<String, Object> platformBody;
}
