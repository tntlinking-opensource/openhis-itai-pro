/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.api.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 电子处方网关调用上下文。 */
@Data
@Accessors(chain = true)
public class ElepInvokeContext {

    private Long tenantId;
    private Long operatorId;
    private String operatorName;
}
