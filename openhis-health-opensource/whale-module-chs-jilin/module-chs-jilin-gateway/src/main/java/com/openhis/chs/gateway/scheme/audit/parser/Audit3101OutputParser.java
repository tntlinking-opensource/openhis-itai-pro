/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.audit.parser;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.gateway.scheme.audit.model.Audit3101DetailRow;
import com.openhis.chs.gateway.scheme.audit.model.Audit3101Output;
import com.openhis.chs.gateway.scheme.audit.model.Audit3101ViolationRow;

import java.util.ArrayList;
import java.util.List;

/**
 * 【3101】output 解析。
 *
 * <p>联调确认 {@code result} 为数组；兼容单对象包成单元素列表（C3 读侧）。</p>
 */
public final class Audit3101OutputParser {

    private Audit3101OutputParser() {
    }

    /**
     * 解析平台 output 为强类型结果。
     *
     * @param body 网关响应
     * @return Output（result 永不为 null）
     */
    public static Audit3101Output parse(ResultBody body) {
        Audit3101Output output = new Audit3101Output();
        output.setResult(new ArrayList<>());
        if (body == null || body.getOutput() == null || body.getOutput().isBlank()) {
            return output;
        }
        String raw = body.getOutput().trim();
        JSONObject root;
        if (raw.startsWith("[")) {
            // 极少见：整段 output 直接是数组
            JSONArray array = JSON.parseArray(raw);
            output.setResult(parseResultArray(array));
            return output;
        }
        root = JSON.parseObject(raw);
        if (root == null) {
            return output;
        }
        Object resultNode = root.get("result");
        if (resultNode == null) {
            return output;
        }
        if (resultNode instanceof JSONArray array) {
            output.setResult(parseResultArray(array));
            return output;
        }
        if (resultNode instanceof JSONObject single) {
            List<Audit3101ViolationRow> one = new ArrayList<>();
            one.add(single.toJavaObject(Audit3101ViolationRow.class));
            output.setResult(one);
            return output;
        }
        return output;
    }

    /**
     * 解析 result 数组元素；明细节点若为单对象则归一成列表。
     *
     * @param array 平台 result JSON 数组
     * @return 违规行列表
     */
    private static List<Audit3101ViolationRow> parseResultArray(JSONArray array) {
        List<Audit3101ViolationRow> rows = new ArrayList<>();
        if (array == null || array.isEmpty()) {
            return rows;
        }
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            if (item == null) {
                continue;
            }
            Audit3101ViolationRow row = item.toJavaObject(Audit3101ViolationRow.class);
            // 明细可能为单对象：归一成列表，避免后续 NPE
            Object details = item.get("judge_result_detail_dtos");
            if (details instanceof JSONObject detailObj) {
                List<Audit3101DetailRow> list = new ArrayList<>();
                list.add(detailObj.toJavaObject(Audit3101DetailRow.class));
                row.setJudgeResultDetailDtos(list);
            }
            rows.add(row);
        }
        return rows;
    }
}
