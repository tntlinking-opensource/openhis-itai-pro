/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.gateway.client.ResultBody;

/** 平台 output 解析（gateway 传输层，无 Spring 依赖） */
public final class ChsGatewayOutputSupport {

    private ChsGatewayOutputSupport() {
    }

    /** 从 9001 应答 output 解析 {@code sign_no} */
    public static String parseSignNo(ResultBody body) {
        if (body == null || body.getOutput() == null || body.getOutput().isBlank()) {
            return null;
        }
        JSONObject root = JSON.parseObject(body.getOutput());
        JSONObject sign = root.getJSONObject("signinoutb");
        if (sign == null) {
            sign = root.getJSONObject("output");
        }
        if (sign == null) {
            return null;
        }
        return sign.getString("sign_no");
    }

    public static JSONObject parseOutputObject(ResultBody body) {
        if (body == null || body.getOutput() == null || body.getOutput().isBlank()) {
            return new JSONObject();
        }
        return JSON.parseObject(body.getOutput());
    }
}
