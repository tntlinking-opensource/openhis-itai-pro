/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.elep.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 取药结果查询结果。 */
@Data
@Accessors(chain = true)
public class ElepMedResult {

    private boolean success;
    private String medResultJson;
    private String message;
}
