/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.inpatient.parser;

import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.common.model.ChsSchemeJsonSupport;
import com.zxplat.plugin.chs.gateway.scheme.common.model.ChsSetlinfoOutput;
import com.zxplat.plugin.chs.gateway.support.ChsPlatformResponseSupport;

/** 【2305】output.setlinfo 解析 */
public final class Ip2305OutputParser {

    private Ip2305OutputParser() {
    }

    public static ChsSetlinfoOutput requireSetlinfo(ResultBody body) {
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.INPATIENT_SETTLE_REVOKE);
        ChsSetlinfoOutput setlinfo = ChsSchemeJsonSupport.parseNested(body, "setlinfo", ChsSetlinfoOutput.class);
        if (setlinfo != null) {
            return setlinfo;
        }
        return ChsSchemeJsonSupport.parseOutput(body, ChsSetlinfoOutput.class);
    }
}
