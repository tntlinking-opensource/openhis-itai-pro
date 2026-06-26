/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.outpatient.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.ChsInfNo;
import com.openhis.chs.api.ChsPlatformCodes;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.domain.ChsErrorCodes;
import com.openhis.chs.domain.support.ChsTextSupport;
import com.openhis.whale.core.application.exception.BusinessException;

/** 平台 ResultBody 解析辅助。 */
public final class ChsPlatformResultSupport {

    private ChsPlatformResultSupport() {
    }

    public static void requireSuccess(ResultBody body, String infNo) {
        if (body == null) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, infNo + " 平台无响应");
        }
        if (!ChsPlatformCodes.INFCODE_SUCCESS.equals(body.getInfcode())) {
            throw new BusinessException(
                ChsErrorCodes.PLATFORM_FAILED,
                infNo + " 失败: " + ChsTextSupport.platformErrMsg(body, "未知错误"));
        }
    }

    public static JSONObject parseOutputObject(ResultBody body) {
        if (body == null || StringUtilsBlank(body.getOutput())) {
            return new JSONObject();
        }
        return JSON.parseObject(body.getOutput());
    }

    public static JSONObject requireSetlinfo(ResultBody body) {
        requireSuccess(body, "2206");
        JSONObject output = parseOutputObject(body);
        JSONObject setlinfo = output.getJSONObject("setlinfo");
        if (setlinfo == null) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "2206 缺少 setlinfo");
        }
        return setlinfo;
    }

    public static JSONObject require2207Setlinfo(ResultBody body) {
        requireSuccess(body, "2207");
        JSONObject output = parseOutputObject(body);
        JSONObject setlinfo = output.getJSONObject("setlinfo");
        if (setlinfo == null) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "2207 缺少 setlinfo");
        }
        return setlinfo;
    }

    public static JSONObject require2303Setlinfo(ResultBody body) {
        requireSuccess(body, "2303");
        JSONObject output = parseOutputObject(body);
        JSONObject setlinfo = output.getJSONObject("setlinfo");
        if (setlinfo == null) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "2303 缺少 setlinfo");
        }
        return setlinfo;
    }

    public static JSONObject require2304Setlinfo(ResultBody body) {
        requireSuccess(body, "2304");
        JSONObject output = parseOutputObject(body);
        JSONObject setlinfo = output.getJSONObject("setlinfo");
        if (setlinfo == null) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "2304 缺少 setlinfo");
        }
        return setlinfo;
    }

    public static JSONObject require2305Setlinfo(ResultBody body) {
        requireSuccess(body, ChsInfNo.INPATIENT_SETTLE_REVOKE);
        JSONObject output = parseOutputObject(body);
        JSONObject setlinfo = output.getJSONObject("setlinfo");
        if (setlinfo == null) {
            return output;
        }
        return setlinfo;
    }

    public static JSONObject require2208Setlinfo(ResultBody body) {
        requireSuccess(body, ChsInfNo.OUTPATIENT_SETTLE_REVOKE);
        JSONObject output = parseOutputObject(body);
        JSONObject setlinfo = output.getJSONObject("setlinfo");
        if (setlinfo == null) {
            return output;
        }
        return setlinfo;
    }

    private static boolean StringUtilsBlank(String value) {
        return value == null || value.isBlank();
    }
}
