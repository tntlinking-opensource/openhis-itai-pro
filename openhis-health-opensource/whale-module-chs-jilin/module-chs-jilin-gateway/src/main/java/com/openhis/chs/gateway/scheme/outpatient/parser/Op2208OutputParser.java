/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.outpatient.parser;

import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.common.model.ChsSchemeJsonSupport;
import com.openhis.chs.gateway.scheme.common.model.ChsSetlinfoOutput;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;

/** 【2208】output.setlinfo 解析 */
public final class Op2208OutputParser {

    private Op2208OutputParser() {
    }

    public static ChsSetlinfoOutput requireSetlinfo(ResultBody body) {
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.OUTPATIENT_SETTLE_REVOKE);
        ChsSetlinfoOutput setlinfo = ChsSchemeJsonSupport.parseNested(body, "setlinfo", ChsSetlinfoOutput.class);
        if (setlinfo != null) {
            return setlinfo;
        }
        return ChsSchemeJsonSupport.parseOutput(body, ChsSetlinfoOutput.class);
    }
}
