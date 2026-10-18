/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.outpatient.parser;

import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.support.ChsPlatformResponseSupport;

/** 【2203】output 校验 */
public final class Op2203OutputParser {

    private Op2203OutputParser() {
    }

    public static void requireSuccess(ResultBody body) {
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.OUTPATIENT_VISIT_UPLOAD);
    }
}
