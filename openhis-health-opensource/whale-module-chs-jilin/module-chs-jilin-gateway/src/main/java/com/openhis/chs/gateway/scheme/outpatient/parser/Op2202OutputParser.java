/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.outpatient.parser;

import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;

/** 【2202】output 校验 */
public final class Op2202OutputParser {

    private Op2202OutputParser() {
    }

    public static void requireSuccess(ResultBody body) {
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.OUTPATIENT_REG_REVOKE);
    }
}
