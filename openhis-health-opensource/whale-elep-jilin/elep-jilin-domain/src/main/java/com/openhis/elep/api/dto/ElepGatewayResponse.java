/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.api.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 电子处方网关原始响应。 */
@Data
@Accessors(chain = true)
public class ElepGatewayResponse {

    private boolean success;
    private String code;
    private String message;
    private String bodyJson;
}
