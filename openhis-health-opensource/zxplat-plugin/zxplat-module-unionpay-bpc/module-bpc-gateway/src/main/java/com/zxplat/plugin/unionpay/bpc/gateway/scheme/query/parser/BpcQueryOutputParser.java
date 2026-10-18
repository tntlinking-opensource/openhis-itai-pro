/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.gateway.scheme.query.parser;

import com.zxplat.plugin.unionpay.bpc.gateway.scheme.share.model.BpcInvokeResult;
import com.zxplat.plugin.unionpay.bpc.gateway.scheme.share.parser.BpcResponseParser;

/** 查单 HTTPS 响应解析（占位）。 */
public final class BpcQueryOutputParser {

    private BpcQueryOutputParser() {
    }

    public static BpcInvokeResult parse(String rawJson) {
        return BpcResponseParser.parse(rawJson);
    }
}
