/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.inpatient.parser;

import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.common.model.ChsSchemeJsonSupport;
import com.openhis.chs.gateway.scheme.common.model.ChsSetlinfoOutput;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import com.openhis.whale.core.application.exception.BusinessException;

/** 【2304】output.setlinfo 解析 */
public final class Ip2304OutputParser {

    private Ip2304OutputParser() {
    }

    public static ChsSetlinfoOutput requireSetlinfo(ResultBody body) {
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.INPATIENT_SETTLE);
        ChsSetlinfoOutput setlinfo = ChsSchemeJsonSupport.parseNested(body, "setlinfo", ChsSetlinfoOutput.class);
        if (setlinfo == null) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "2304 缺少 setlinfo");
        }
        return setlinfo;
    }
}
