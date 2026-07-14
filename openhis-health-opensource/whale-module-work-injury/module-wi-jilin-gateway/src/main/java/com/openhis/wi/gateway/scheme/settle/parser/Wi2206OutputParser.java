/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.settle.parser;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.wi.gateway.scheme.settle.model.Wi2206Output;

import java.util.Map;

/** 【2206】预结算 output 解析。 */
public final class Wi2206OutputParser {

    private Wi2206OutputParser() {
    }

    /**
     * 解析 Win 回传的 2206 output Map。
     *
     * @param output Win output
     * @return 预结算出参（含 mdtrt_id）
     */
    public static Wi2206Output parse(Map<String, Object> output) {
        if (output == null || output.isEmpty()) {
            return new Wi2206Output();
        }
        return JSON.parseObject(JSON.toJSONString(unwrapSingleRow(output)), Wi2206Output.class);
    }

    /** Win 回包可能是 {@code {output:{...}}} 或单层 Map，归一化为单行 JSON 对象。 */
    private static Object unwrapSingleRow(Map<String, Object> output) {
        Object direct = output.get("output");
        if (direct instanceof Map<?, ?> map) {
            return map;
        }
        if (direct instanceof JSONObject json) {
            return json;
        }
        if (output.size() == 1) {
            Object sole = output.values().iterator().next();
            if (sole instanceof Map<?, ?>) {
                return sole;
            }
        }
        return output;
    }
}
