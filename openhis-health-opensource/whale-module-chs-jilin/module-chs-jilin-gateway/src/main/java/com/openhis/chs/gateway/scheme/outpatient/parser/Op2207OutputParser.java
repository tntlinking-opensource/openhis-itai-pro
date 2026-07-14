/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.outpatient.parser;

import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.common.model.ChsSchemeJsonSupport;
import com.openhis.chs.gateway.scheme.common.model.ChsSetlinfoOutput;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import com.openhis.whale.core.application.exception.BusinessException;

/** 【2207】output.setlinfo 解析 */
public final class Op2207OutputParser {

    private Op2207OutputParser() {
    }

    public static ChsSetlinfoOutput requireSetlinfo(ResultBody body) {
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.OUTPATIENT_SETTLE);
        ChsSetlinfoOutput setlinfo = ChsSchemeJsonSupport.parseNested(body, "setlinfo", ChsSetlinfoOutput.class);
        if (setlinfo == null) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "2207 缺少 setlinfo");
        }
        return setlinfo;
    }
}
