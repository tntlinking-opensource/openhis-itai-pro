/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.gateway.scheme.push.parser;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.whale.crb.domain.push.result.PushResult;

/**
 * 前置软件 receive 标准响应体解析器（P10-C/D）。
 *
 * <p><b>职责：</b>将前置软件 HTTP 响应 JSON 解析为 domain {@link PushResult}，统一成功/失败字段映射。</p>
 * <p><b>实现位置：</b>本类（gateway {@code scheme.push.parser}）</p>
 * <p><b>调用方：</b>gateway {@code FrontSoftwareResponseParser} 薄委托、{@code NationalFrontSoftwareClient}</p>
 * <p><b>架构边界：</b>gateway 解析层；domain 只接收 {@link PushResult}，不依赖 fastjson 细节。</p>
 * <p><b>迁移阶段：</b>P10-C/D</p>
 */
public final class FrontSoftwarePushResponseParser {

    private FrontSoftwarePushResponseParser() {
    }

    /**
     * 解析前置软件标准响应 JSON。
     *
     * <p>平台 JSON key：{@code result}（boolean）、{@code id}、{@code errorCode}、{@code errorName}、{@code desc}。</p>
     *
     * @param body HTTP 响应体；空或非法 JSON 时返回带 CRB-PARSE 错误码的失败结果
     * @return domain 推送结果
     */
    public static PushResult parse(String body) {
        if (body == null || body.isBlank()) {
            return new PushResult()
                    .setSuccess(false)
                    .setErrorCode("CRB-PARSE-001")
                    .setErrorName("空响应")
                    .setDescription("前置返回空 body");
        }
        JSONObject json = JSONObject.parseObject(body);
        if (json == null) {
            return new PushResult()
                    .setSuccess(false)
                    .setErrorCode("CRB-PARSE-002")
                    .setErrorName("JSON 解析失败")
                    .setDescription(body);
        }
        boolean success = json.getBooleanValue("result");
        return new PushResult()
                .setSuccess(success)
                .setRemoteId(json.getString("id"))
                .setErrorCode(json.getString("errorCode"))
                .setErrorName(json.getString("errorName"))
                .setDescription(json.getString("desc"));
    }
}
