/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.gateway.scheme.register.parser;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.zxplat.plugin.wi.gateway.scheme.register.model.Wi9102Output;
import com.zxplat.plugin.wi.gateway.scheme.register.model.Wi9102QualificationRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 【9102】审批信息查询 output 解析。 */
public final class Wi9102OutputParser {

    private Wi9102OutputParser() {
    }

    /**
     * 解析 Win 回传的 9102 output Map。
     *
     * @param output Win output
     * @return 审批行列表（可能为空）
     */
    public static Wi9102Output parse(Map<String, Object> output) {
        Wi9102Output result = new Wi9102Output();
        if (output == null || output.isEmpty()) {
            return result;
        }
        JSONArray rows = extractArray(output);
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        List<Wi9102QualificationRow> parsed = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Object item = rows.get(i);
            Wi9102QualificationRow row = JSON.parseObject(JSON.toJSONString(item), Wi9102QualificationRow.class);
            if (row == null) {
                continue;
            }
            if (isEmpty(row.getQualificationId())) {
                // 兼容未映射到 alternateNames 的原始 Map
                String referralId = extractField(item, "referral_id", "referralId");
                if (isNotEmpty(referralId)) {
                    row.setQualificationId(referralId);
                }
            }
            if (isNotEmpty(row.getQualificationId())) {
                parsed.add(row);
            }
        }
        result.setRows(parsed);
        return result;
    }

    private static String extractField(Object item, String... keys) {
        if (item == null || keys == null) {
            return null;
        }
        com.alibaba.fastjson2.JSONObject json = item instanceof com.alibaba.fastjson2.JSONObject obj
                ? obj
                : JSON.parseObject(JSON.toJSONString(item));
        if (json == null) {
            return null;
        }
        for (String key : keys) {
            Object value = json.get(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (isNotEmpty(text) && !"null".equalsIgnoreCase(text) && !"0".equals(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    /** 9102 output 可能是数组、rows/list 包装、或 Win 解析失败时的 raw 整包。 */
    private static JSONArray extractArray(Map<String, Object> output) {
        Object direct = output.get("output");
        if (direct instanceof List<?> list) {
            return new JSONArray(list);
        }
        if (direct instanceof JSONArray array) {
            return array;
        }
        Object raw = output.get("raw");
        if (raw instanceof String rawText && !rawText.isBlank()) {
            try {
                com.alibaba.fastjson2.JSONObject envelope = JSON.parseObject(rawText);
                if (envelope != null) {
                    Object nested = envelope.get("output");
                    if (nested instanceof List<?> list) {
                        return new JSONArray(list);
                    }
                    if (nested instanceof JSONArray array) {
                        return array;
                    }
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        for (String key : List.of("rows", "list", "data", "result", "approveinfo")) {
            Object candidate = output.get(key);
            if (candidate instanceof List<?> list) {
                return new JSONArray(list);
            }
            if (candidate instanceof JSONArray array) {
                return array;
            }
        }
        if (output.size() == 1) {
            Object sole = output.values().iterator().next();
            if (sole instanceof List<?> list) {
                return new JSONArray(list);
            }
            if (sole instanceof JSONArray array) {
                return array;
            }
        }
        return null;
    }
}
