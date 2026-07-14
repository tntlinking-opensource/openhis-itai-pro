/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.register.parser;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.openhis.wi.gateway.scheme.register.model.Wi9102Output;
import com.openhis.wi.gateway.scheme.register.model.Wi9102QualificationRow;

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
            if (row != null && isNotEmpty(row.getQualificationId())) {
                parsed.add(row);
            }
        }
        result.setRows(parsed);
        return result;
    }

    /** 9102 output 可能是数组、rows/list 包装或单键 Map，归一化为 JSONArray。 */
    private static JSONArray extractArray(Map<String, Object> output) {
        Object direct = output.get("output");
        if (direct instanceof List<?> list) {
            return new JSONArray(list);
        }
        if (direct instanceof JSONArray array) {
            return array;
        }
        for (String key : List.of("rows", "list", "data", "result")) {
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
