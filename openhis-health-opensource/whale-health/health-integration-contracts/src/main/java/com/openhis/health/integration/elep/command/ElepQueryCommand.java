/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.elep.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 电子处方查询命令。 */
@Data
@Accessors(chain = true)
public class ElepQueryCommand {

    private Long prescriptionId;
    private Long contractId;
    private String hiRxno;
}
