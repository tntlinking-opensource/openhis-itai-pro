/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.gateway.scheme.share.model;

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
