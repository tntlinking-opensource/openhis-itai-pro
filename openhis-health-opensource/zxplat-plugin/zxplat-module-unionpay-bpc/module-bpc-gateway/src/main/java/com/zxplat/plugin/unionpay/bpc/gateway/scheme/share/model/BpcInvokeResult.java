/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.gateway.scheme.share.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** BPC HTTPS 调用结果（线协议占位）。 */
@Data
@Accessors(chain = true)
public class BpcInvokeResult {

    private boolean success;
    private String respCode;
    private String respMsg;
    private String rawJson;
}
