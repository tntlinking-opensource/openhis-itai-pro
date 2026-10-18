/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.support;

import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.plugin.chs.domain.share.constants.ChsPlatformCodes;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.gateway.scheme.common.model.ChsFeeDetailLine;
import com.zxplat.framework.core.application.exception.BusinessException;

import java.util.List;

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

    /**
     * 校验费用上传响应；目录未命中时在原文前写明本批药品、耗材或诊疗。
     *
     * @param body  平台响应
     * @param infNo 交易号（2204/2301）
     * @param lines 本批费用行（含本地项目名）
     */
    public static void requireFeeUploadSuccess(ResultBody body, String infNo, List<ChsFeeDetailLine> lines) {
        // 无响应：无法对照明细
        if (body == null) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, infNo + " 平台无响应");
        }
        // 成功则不改写
        if (ChsPlatformCodes.INFCODE_SUCCESS.equals(body.getInfcode())) {
            return;
        }
        String errMsg = FeeUploadCatalogMissMessageSupport.decorate(body.getErrMsg(), lines);
        throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, infNo + " 平台失败: " + errMsg);
    }
}
