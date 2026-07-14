/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.support;

import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.domain.share.constants.ChsPlatformCodes;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.whale.core.application.exception.BusinessException;

/** 平台 ResultBody 校验 */
public final class ChsPlatformResponseSupport {

    private ChsPlatformResponseSupport() {
    }

    /**
     * @param body 平台响应
     * @param infNo 交易号（用于错误提示） */
    public static void requireSuccess(ResultBody body, String infNo) {
        if (body == null) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, infNo + " 平台无响应");
        }
        if (!ChsPlatformCodes.INFCODE_SUCCESS.equals(body.getInfcode())) {
            throw new BusinessException(
                ChsErrorCodes.PLATFORM_FAILED,
                infNo + " 平台失败: " + body.getErrMsg());
        }
    }
}
