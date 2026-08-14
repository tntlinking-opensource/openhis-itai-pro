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

/** 【2206】output.setlinfo 解析 */
public final class Op2206OutputParser {

    private Op2206OutputParser() {
    }

    public static ChsSetlinfoOutput requireSetlinfo(ResultBody body) {
        requirePreSettleSuccess(body);
        ChsSetlinfoOutput setlinfo = ChsSchemeJsonSupport.parseNested(body, "setlinfo", ChsSetlinfoOutput.class);
        if (setlinfo == null) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "2206 缺少 setlinfo");
        }
        return setlinfo;
    }

    /** 2206 失败时对「门慢登记」类错误追加医疗类别核对提示。 */
    private static void requirePreSettleSuccess(ResultBody body) {
        try {
            ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.OUTPATIENT_PRE_SETTLE);
        } catch (BusinessException ex) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("门慢登记")) {
                throw new BusinessException(
                    ChsErrorCodes.PLATFORM_FAILED,
                    msg + "；请核对诊断医疗类别是否应为门诊特病(140201)而非门诊慢病(140104)");
            }
            throw ex;
        }
    }
}
