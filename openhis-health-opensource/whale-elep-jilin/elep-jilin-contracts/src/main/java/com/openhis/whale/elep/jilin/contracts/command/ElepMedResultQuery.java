/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.elep.jilin.contracts.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 取药结果查询 Port 入参。 */
@Data
@Accessors(chain = true)
public class ElepMedResultQuery {

    private Long contractId;
    private String contractBusNo;
    private String hiRxno;
    private java.util.Map<String, Object> platformBody;
}
