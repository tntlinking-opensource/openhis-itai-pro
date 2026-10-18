/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.gateway.scheme.share.parser;

import com.zxplat.plugin.unionpay.bpc.gateway.scheme.share.model.BpcInvokeResult;

/** BPC HTTPS 响应解析（占位）。 */
public final class BpcResponseParser {

    private BpcResponseParser() {
    }

    public static BpcInvokeResult parse(String rawJson) {
        return new BpcInvokeResult()
                .setSuccess(false)
                .setRawJson(rawJson);
    }
}
