/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.medicalinfo.parser;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.domain.medicalinfo.result.Setl5203Result;
import com.zxplat.plugin.chs.gateway.client.ResultBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 【5203】结算信息 output 解析 */
public final class Med5203OutputParser {

    private static final String SETLDETAIL_KEY = "setldetail";

    private Med5203OutputParser() {
    }

    public static Setl5203Result parse5203(ResultBody body, String infno) {
        Setl5203Result result = new Setl5203Result();
        result.setInfno(infno);
        JSONObject settlement = resolveSettlementObject(parseRootObject(body));
        if (settlement == null || settlement.isEmpty()) {
            return result;
        }
        JSONObject copy = new JSONObject(settlement);
        Object setlDetailRaw = copy.remove(SETLDETAIL_KEY);
        if (setlDetailRaw == null) {
            setlDetailRaw = copy.remove("setl_detail");
        }
        result.setHeader(new HashMap<>(copy));
        result.setSetlDetails(toRowList(setlDetailRaw));
        return result;
    }

    private static JSONObject resolveSettlementObject(JSONObject root) {
        if (root == null || root.isEmpty()) {
            return new JSONObject();
        }
        if (looksLikeSettlement(root)) {
            return root;
        }
        for (String key : root.keySet()) {
            Object value = root.get(key);
            if (value instanceof JSONObject object && looksLikeSettlement(object)) {
                return object;
            }
        }
        return root;
    }

    private static boolean looksLikeSettlement(JSONObject root) {
        return root.containsKey("setl_id")
            || root.containsKey("setlId")
            || root.containsKey("medfee_sumamt")
            || root.containsKey("fund_pay_sumamt");
    }

    private static JSONObject parseRootObject(ResultBody body) {
        if (body == null || body.getOutput() == null || body.getOutput().isBlank()) {
            return new JSONObject();
        }
        String raw = body.getOutput().trim();
        if (raw.startsWith("[")) {
            JSONArray array = JSON.parseArray(raw);
            if (array != null && !array.isEmpty() && array.get(0) instanceof JSONObject first) {
                return first;
            }
            return new JSONObject();
        }
        return JSON.parseObject(raw);
    }

    private static List<Map<String, Object>> toRowList(Object raw) {
        if (raw instanceof JSONArray array) {
            return fromJsonArray(array);
        }
        if (raw instanceof JSONObject object) {
            return List.of(new HashMap<>(object));
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                if (text.trim().startsWith("[")) {
                    return fromJsonArray(JSON.parseArray(text));
                }
                return List.of(new HashMap<>(JSON.parseObject(text)));
            } catch (RuntimeException ignored) {
                return List.of();
            }
        }
        return List.of();
    }

    private static List<Map<String, Object>> fromJsonArray(JSONArray array) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            Object element = array.get(i);
            if (element instanceof JSONObject object) {
                rows.add(new HashMap<>(object));
            }
        }
        return rows;
    }
}
