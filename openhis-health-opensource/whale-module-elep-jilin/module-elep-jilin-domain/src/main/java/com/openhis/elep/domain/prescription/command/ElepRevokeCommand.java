/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 电子处方撤销命令。 */
@Data
@Accessors(chain = true)
public class ElepRevokeCommand {

    private Long prescriptionId;
    private Long contractId;
    private String contractBusNo;
    private String hiRxno;
    private String revokeReason;
    /** 可选：覆盖自动组装的平台 ElepRevokeInput。 */
    private java.util.Map<String, Object> platformBody;
    /** Extension 路径：撤销医师医保代码。 */
    private String drCode;
    /** Extension 路径：撤销医师姓名。 */
    private String undoDrName;
}
