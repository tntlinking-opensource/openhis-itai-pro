/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.inventory.parser;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.gateway.support.ChsGatewayOutputSupport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 3508–3513 平台进销存查询 output 解析 */
public final class InvPlatformOutputParser {

    private InvPlatformOutputParser() {
    }

    public static List<Map<String, Object>> parseQueryRows(ResultBody body) {
        JSONObject output = ChsGatewayOutputSupport.parseOutputObject(body);
        return parseQueryRows(output);
    }

    public static List<Map<String, Object>> parseQueryRows(JSONObject output) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (output == null || output.isEmpty()) {
            return rows;
        }
        for (String key : output.keySet()) {
            Object value = output.get(key);
            if (value instanceof JSONArray array) {
                appendJsonArrayRows(rows, array);
                if (!rows.isEmpty()) {
                    return rows;
                }
            }
        }
        if (output.containsKey("medListCodg") || output.containsKey("med_list_codg")
            || output.containsKey("fixmedinsBchno") || output.containsKey("fixmedins_bchno")) {
            rows.add(new HashMap<>(output));
        }
        return rows;
    }

    public static String readSaleUploadField(JSONObject platformResult, String camelKey, String snakeKey) {
        if (platformResult == null) {
            return null;
        }
        String value = platformResult.getString(camelKey);
        if (value == null || value.isBlank()) {
            value = platformResult.getString(snakeKey);
        }
        return value;
    }

    private static void appendJsonArrayRows(List<Map<String, Object>> rows, JSONArray array) {
        if (array == null || array.isEmpty()) {
            return;
        }
        for (int i = 0; i < array.size(); i++) {
            Object element = array.get(i);
            if (element instanceof JSONObject object) {
                rows.add(new HashMap<>(object));
            }
        }
    }
}
