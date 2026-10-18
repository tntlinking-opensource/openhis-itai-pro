/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.session.transport;

import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.gateway.http.ChsHttpAccessor;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.session.Sign9001Input;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【9001】签到单次平台 HTTP 出站（不含会话缓存与 opter 轮换）。
 *
 * <p>命名使用 {@code *Transport}，与 bridge/application 业务 {@code *Invoker} 区分。</p>
 */
@Component
@RequiredArgsConstructor
public class Sign9001Transport {

    private final ChsHttpAccessor chsHttpAccessor;

    /**
     * 发送 9001 签到报文。
     *
     * @param contractBusNo  医保契约 bus_no
     * @param signIn         签到 signIn 节点
     * @param invokeContext  操作员上下文
     * @return 平台原始响应体
     */
    public ResultBody send(String contractBusNo, Sign9001Input signIn, InvokeContext invokeContext) {
        JSONObject input = new JSONObject();
        input.put("signIn", signIn);
        return chsHttpAccessor.send(contractBusNo, ChsInfNo.SIGN_IN, input, invokeContext);
    }
}
