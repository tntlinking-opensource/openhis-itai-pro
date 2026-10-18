/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.gateway.session;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SignUtil {
    private static final List<String> ignoreSign = new ArrayList<>();

    public static String getSignText(JSONObject jsonObject, String appSecret) {
        Map<String, String> signMap = new TreeMap<>();
        for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
            if (!ElepTextSupport.isEmpty(entry.getValue()) && !ignoreSign.contains(entry.getKey())) {
                signMap.put(entry.getKey(), getValue(entry.getValue()));
            }
        }

        List<String> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : signMap.entrySet()) {
            if (ElepTextSupport.isNotEmpty(getObjString(entry.getValue()))) {
                list.add(entry.getKey() + "=" + entry.getValue() + "&");
            }
        }

        String[] arrayToSort = list.toArray(new String[0]);
        Arrays.sort(arrayToSort, String.CASE_INSENSITIVE_ORDER);
        StringBuilder sb = new StringBuilder();
        for (String part : arrayToSort) {
            sb.append(part);
        }

        return sb.append("key=").append(appSecret).toString();
    }

    public static String getObjString(Object object) {
        return object == null ? "" : (String) object;
    }

    public static String getValue(Object value) {
        return value instanceof String ? getObjString(value) : treeJsonParam(value);
    }

    private static String treeJsonParam(Object value) {
        if (value instanceof Map<?, ?> nestedMap) {
            Map<String, Object> treeNestedMap = toStringKeyedMap(nestedMap);
            return JSONObject.toJSONString(treeParams(treeNestedMap));
        }
        if (value instanceof ArrayList<?> ar) {
            return JSONObject.toJSONString(treeList(ar));
        }
        if (value instanceof JSONArray jarr) {
            return JSONObject.toJSONString(treeJsonArray(jarr));
        }
        return value.toString();
    }

    private static Map<String, Object> treeParams(Map<String, Object> params) {
        if (params == null) {
            return new TreeMap<>();
        }
        Map<String, Object> treeParams = new TreeMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nestedMap) {
                treeParams.put(key, treeParams(toStringKeyedMap(nestedMap)));
            } else if (value instanceof ArrayList<?> ar) {
                treeParams.put(key, treeList(ar));
            } else if (value instanceof JSONArray ar) {
                treeParams.put(key, treeJsonArray(ar));
            } else if (value != null && !"".equals(value)) {
                treeParams.put(key, value.toString());
            }
        }
        return treeParams;
    }

    private static JSONArray treeList(List<?> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        JSONArray jsonArray = new JSONArray();
        for (int i = 0; i < list.size(); ++i) {
            jsonArray.add(i, list.get(i));
        }
        return treeJsonArray(jsonArray);
    }

    private static JSONArray treeJsonArray(JSONArray jarr) {
        if (jarr == null || jarr.isEmpty()) {
            return null;
        }
        JSONArray jsonArray = new JSONArray();
        for (int i = 0; i < jarr.size(); ++i) {
            Object value = jarr.get(i);
            if (value instanceof Map<?, ?> nestedMap) {
                jsonArray.add(i, treeParams(toStringKeyedMap(nestedMap)));
            } else if (value instanceof ArrayList<?> ar) {
                jsonArray.add(i, treeList(ar));
            } else if (value instanceof JSONArray ar) {
                jsonArray.add(i, treeJsonArray(ar));
            } else if (!"".equals(value)) {
                jsonArray.add(i, value.toString());
            }
        }
        return jsonArray;
    }

    private static Map<String, Object> toStringKeyedMap(Map<?, ?> nestedMap) {
        Map<String, Object> treeNestedMap = new TreeMap<>();
        for (Map.Entry<?, ?> nestedEntry : nestedMap.entrySet()) {
            treeNestedMap.put(String.valueOf(nestedEntry.getKey()), nestedEntry.getValue());
        }
        return treeNestedMap;
    }

    static {
        ignoreSign.add("signData");
        ignoreSign.add("encData");
        ignoreSign.add("extra");
    }
}
