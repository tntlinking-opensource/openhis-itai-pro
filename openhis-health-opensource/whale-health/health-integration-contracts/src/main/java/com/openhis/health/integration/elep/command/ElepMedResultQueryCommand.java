/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.elep.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 取药结果查询命令。 */
@Data
@Accessors(chain = true)
public class ElepMedResultQueryCommand {

    private Long contractId;
    private String hiRxno;
}
