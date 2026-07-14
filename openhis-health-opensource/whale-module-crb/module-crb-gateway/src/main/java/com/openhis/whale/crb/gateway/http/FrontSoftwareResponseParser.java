/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.gateway.http;

import com.openhis.whale.crb.domain.push.result.PushResult;
import com.openhis.whale.crb.gateway.scheme.push.parser.FrontSoftwarePushResponseParser;

/**
 * 解析前置软件标准响应体（http 层薄委托 scheme parser）。
 */
public final class FrontSoftwareResponseParser {

    private FrontSoftwareResponseParser() {
    }

    public static PushResult parse(String body) {
        return FrontSoftwarePushResponseParser.parse(body);
    }
}
