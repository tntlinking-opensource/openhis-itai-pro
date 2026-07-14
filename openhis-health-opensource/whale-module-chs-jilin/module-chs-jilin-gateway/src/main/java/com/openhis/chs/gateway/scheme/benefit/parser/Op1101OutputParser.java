/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.benefit.parser;

import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.common.model.ChsSchemeJsonSupport;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.chs.gateway.scheme.benefit.model.Op1101Output;

/** 【1101】output 解析与校验 */
public final class Op1101OutputParser {

    private Op1101OutputParser() {
    }

    /** 平台成功校验后解析 baseinfo / insuinfo / idetinfo；缺少 baseinfo 时抛错 */
    public static Op1101Output requireOutput(ResultBody body) {
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.PERSON_INFO);
        Op1101Output output = ChsSchemeJsonSupport.parseOutput(body, Op1101Output.class);
        if (output == null || output.getBaseinfo() == null) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "1101 缺少 baseinfo");
        }
        return output;
    }
}
