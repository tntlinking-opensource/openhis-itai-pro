/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.elep.jilin.contracts.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 电子处方查询 Port 命令。 */
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
