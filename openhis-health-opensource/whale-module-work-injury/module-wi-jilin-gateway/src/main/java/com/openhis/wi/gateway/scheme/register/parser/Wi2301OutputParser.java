/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.register.parser;

import com.alibaba.fastjson2.JSON;
import com.openhis.wi.gateway.scheme.register.model.Wi2301Output;

import java.util.Map;

/** 【2301】审批信息上传 output 解析。 */
public final class Wi2301OutputParser {

    private Wi2301OutputParser() {
    }

    /**
     * 解析 Win 回传的 2301 output Map。
     *
     * @param output Win output
     * @return 2301 出参；无 qualification_id 时字段为空
     */
    public static Wi2301Output parse(Map<String, Object> output) {
        if (output == null || output.isEmpty()) {
            return new Wi2301Output();
        }
        return JSON.parseObject(JSON.toJSONString(output), Wi2301Output.class);
    }
}
