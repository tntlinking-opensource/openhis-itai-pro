/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.gateway.scheme.register.parser;

import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.wi.gateway.scheme.register.model.Wi1101Output;

import java.util.Map;

/** 【1101】人员信息查询 output 解析。 */
public final class Wi1101OutputParser {

    private Wi1101OutputParser() {
    }

    /**
     * 解析 Win 回传的 1101 output Map。
     *
     * @param output Win output（可为 {@code {output:{...}}} 或已解包对象）
     * @return psn_no / injureAffirmId
     */
    public static Wi1101Output parse(Map<String, Object> output) {
        Wi1101Output result = new Wi1101Output();
        if (output == null || output.isEmpty()) {
            return result;
        }
        JSONObject root = unwrap(output);
        if (root == null || root.isEmpty()) {
            return result;
        }
        String psnNo = firstNonBlank(
                root.getString("psn_no"),
                root.getString("psnNo"));
        result.setPsnNo(psnNo);

        JSONArray diagnosis = root.getJSONArray("diagnosis");
        if (diagnosis != null && !diagnosis.isEmpty()) {
            Object first = diagnosis.get(0);
            if (first != null) {
                JSONObject row = first instanceof JSONObject json
                        ? json
                        : JSON.parseObject(JSON.toJSONString(first));
                if (row != null) {
                    Object affirm = row.get("injureAffirmID");
                    if (affirm == null) {
                        affirm = row.get("injureAffirmId");
                    }
                    if (affirm != null) {
                        String id = String.valueOf(affirm).trim();
                        if (isNotEmpty(id) && !"null".equalsIgnoreCase(id) && !"0".equals(id)) {
                            result.setInjureAffirmId(id);
                        }
                    }
                }
            }
        }
        return result;
    }

    private static JSONObject unwrap(Map<String, Object> output) {
        Object direct = output.get("output");
        if (direct instanceof Map<?, ?> map) {
            return new JSONObject(map);
        }
        if (direct instanceof JSONObject json) {
            return json;
        }
        if (output.containsKey("psn_no") || output.containsKey("psnNo") || output.containsKey("diagnosis")) {
            return new JSONObject(output);
        }
        if (output.size() == 1) {
            Object sole = output.values().iterator().next();
            if (sole instanceof Map<?, ?> map) {
                return new JSONObject(map);
            }
            if (sole instanceof JSONObject json) {
                return json;
            }
        }
        return new JSONObject(output);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (isNotEmpty(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
