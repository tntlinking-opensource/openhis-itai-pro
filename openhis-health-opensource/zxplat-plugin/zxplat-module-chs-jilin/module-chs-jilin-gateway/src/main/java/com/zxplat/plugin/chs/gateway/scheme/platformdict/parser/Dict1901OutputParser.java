/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.platformdict.parser;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.domain.platformdict.command.UpsertPlatformDictRowCommand;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.gateway.scheme.platformdict.model.Dict1901Row;

import java.util.ArrayList;
import java.util.List;
import com.zxplat.plugin.chs.gateway.scheme.platformdict.model.Dict1901Row;

/** 【1901】字典查询 output 解析 */
public final class Dict1901OutputParser {

    private Dict1901OutputParser() {
    }

    public static JSONArray parseList(ResultBody body) {
        if (body == null || body.getOutput() == null || body.getOutput().isBlank()) {
            return new JSONArray();
        }
        String raw = body.getOutput().trim();
        if (raw.startsWith("[")) {
            return JSON.parseArray(raw);
        }
        JSONObject root = JSON.parseObject(raw);
        JSONArray list = firstNonEmpty(
            root.getJSONArray("list"),
            nestedList(root, "output"),
            nestedList(root, "data"),
            nestedList(root, "result"));
        return list != null ? list : new JSONArray();
    }

    public static List<UpsertPlatformDictRowCommand> parseRows(String dictType, ResultBody body) {
        JSONArray list = parseList(body);
        List<UpsertPlatformDictRowCommand> rows = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            JSONObject jo = list.getJSONObject(i);
            Dict1901Row parsed = Dict1901Row.fromJSONObject(jo);
            if (parsed == null || !parsed.hasValueAndLabel()) {
                continue;
            }
            UpsertPlatformDictRowCommand row = new UpsertPlatformDictRowCommand()
                .setDictType(dictType)
                .setDictValue(parsed.resolveValue())
                .setDictLabel(parsed.resolveLabel())
                .setParentValue(parsed.resolveParentValue())
                .setValiFlag(parsed.resolveValiFlag())
                .setSortOrder(parsed.resolveSortOrder())
                .setPlatformVersion(parsed.resolvePlatformVersion());
            rows.add(row);
        }
        return rows;
    }

    private static JSONArray nestedList(JSONObject root, String key) {
        if (root == null || key == null) {
            return null;
        }
        JSONObject nested = root.getJSONObject(key);
        return nested != null ? nested.getJSONArray("list") : null;
    }

    private static JSONArray firstNonEmpty(JSONArray... candidates) {
        for (JSONArray candidate : candidates) {
            if (candidate != null && !candidate.isEmpty()) {
                return candidate;
            }
        }
        for (JSONArray candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }
}
