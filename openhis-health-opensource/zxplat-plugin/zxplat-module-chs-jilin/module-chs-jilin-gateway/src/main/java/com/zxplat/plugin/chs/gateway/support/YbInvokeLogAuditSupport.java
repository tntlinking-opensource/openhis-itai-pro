/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.support;

import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.domain.share.constants.ChsPlatformCodes;

/** {@code yb_invoke_log} 审计字段组装（无 IO，供 {@link com.zxplat.plugin.chs.gateway.observability.PersistingInsuranceGatewayClient} 使用） */
public final class YbInvokeLogAuditSupport {

    private YbInvokeLogAuditSupport() {
    }

    /**
     * @param body 平台响应
     * @return infcode；无响应时 {@code null} */
    public static String resolveInfcode(ResultBody body, RuntimeException invokeError) {
        if (body != null && body.getInfcode() != null) {
            return body.getInfcode();
        }
        if (invokeError != null) {
            return "-1";
        }
        return null;
    }

    /**
     * @param body 平台响应
     * @param invokeError HTTP/超时等异常
     * @return 平台 err_msg 或异常摘要 */
    public static String resolveErrMsg(ResultBody body, RuntimeException invokeError) {
        if (invokeError != null) {
            return invokeError.getMessage();
        }
        return extractPlatformErrMsg(body);
    }

    /**
     * @param body 平台响应
     * @return 响应 JSON 全文 */
    public static String serializeResponseBody(ResultBody body) {
        if (body == null) {
            return null;
        }
        return JSON.toJSONString(body);
    }

    /**
     * @param ex 调用异常
     * @return 异常摘要 JSON */
    public static String serializeInvokeException(RuntimeException ex) {
        if (ex == null) {
            return null;
        }
        JSONObject json = new JSONObject();
        json.put("exceptionType", ex.getClass().getSimpleName());
        json.put("exceptionMessage", ex.getMessage());
        return json.toJSONString();
    }

    /**
     * @param input 报文 input 节点
     * @return JSON 字符串 */
    public static String serializeRequest(Object input) {
        if (input == null) {
            return null;
        }
        if (input instanceof String text) {
            return text;
        }
        return JSON.toJSONString(input);
    }

    /**
     * @param body 平台响应
     * @return err_msg 去空白；无则 {@code null} */
    public static String extractPlatformErrMsg(ResultBody body) {
        if (body == null || body.getErrMsg() == null) {
            return null;
        }
        String trimmed = body.getErrMsg().trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    /**
     * @param infno 交易号
     * @param infname 显式名称（可空）
     * @return 中文交易名或 infno */
    public static String resolveInfname(String infno, String infname) {
        if (isNotEmpty(infname)) {
            return infname.trim();
        }
        return ChsInfNo.resolveDisplayName(infno);
    }

    /**
     * @param body 平台响应
     * @return 是否 infcode=0 */
    public static boolean isPlatformSuccess(ResultBody body) {
        return body != null && ChsPlatformCodes.INFCODE_SUCCESS.equals(body.getInfcode());
    }
}
