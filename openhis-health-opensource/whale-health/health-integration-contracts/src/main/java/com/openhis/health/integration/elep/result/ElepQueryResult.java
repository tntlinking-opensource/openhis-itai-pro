/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.elep.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 电子处方查询结果。 */
@Data
@Accessors(chain = true)
public class ElepQueryResult {

    private boolean success;
    private String rxStasCodg;
    private String rxUsedStasCodg;
    private String message;
}
