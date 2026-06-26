/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.elep.jilin.contracts.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 取药结果查询 Port 结果。 */
@Data
@Accessors(chain = true)
public class ElepMedResultResult {

    private boolean success;
    private String medResultJson;
    private String message;
}
