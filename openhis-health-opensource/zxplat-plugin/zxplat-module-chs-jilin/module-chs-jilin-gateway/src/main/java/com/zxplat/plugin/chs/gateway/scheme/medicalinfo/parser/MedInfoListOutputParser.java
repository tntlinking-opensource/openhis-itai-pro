/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.medicalinfo.parser;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.domain.medicalinfo.result.MedicalInfoQueryResult;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 【5201–5302A】列表型 output 解析 */
public final class MedInfoListOutputParser {

    private static final String[] ARRAY_KEYS_5201 = {"mdtrtinfo", "mdtrt_info", "data", "output"};
    private static final String[] ARRAY_KEYS_5202 = {"diseinfo", "dise_info", "data", "output"};
    private static final String[] ARRAY_KEYS_5204 = {"feeListInfo", "fee_list_info", "feedetail", "data", "output"};
    private static final String[] ARRAY_KEYS_5205 = {"feedetail", "fee_detail", "data", "output"};
    private static final String[] ARRAY_KEYS_5206 = {"cuminfo", "cum_info", "data", "output"};
    private static final String[] ARRAY_KEYS_5301 = {"feedetail", "fee_detail", "data", "output"};
    private static final String[] ARRAY_KEYS_5302A = {"result", "feedetail", "data", "output"};

    private MedInfoListOutputParser() {
    }

    public static MedicalInfoQueryResult parse5201(ResultBody body) {
        return rowsResult(ChsInfNo.MDTRT_QUERY, parseOutputRows(body, ARRAY_KEYS_5201));
    }

    public static MedicalInfoQueryResult parse5202(ResultBody body) {
        return rowsResult(ChsInfNo.DISE_QUERY, parseOutputRows(body, ARRAY_KEYS_5202));
    }

    public static MedicalInfoQueryResult parse5204(ResultBody body) {
        return rowsResult(ChsInfNo.FEE_DETAIL_QUERY, parseOutputRows(body, ARRAY_KEYS_5204));
    }

    public static MedicalInfoQueryResult parse5205(ResultBody body) {
        return rowsResult(ChsInfNo.OPSP_DRUG_QUERY, parseOutputRows(body, ARRAY_KEYS_5205));
    }

    public static MedicalInfoQueryResult parse5206(ResultBody body) {
        return rowsResult(ChsInfNo.PERSON_CUM_QUERY, parseOutputRows(body, ARRAY_KEYS_5206));
    }

    public static MedicalInfoQueryResult parse5301(ResultBody body) {
        return rowsResult(ChsInfNo.PERSON_OPSP_FILING_QUERY, parseOutputRows(body, ARRAY_KEYS_5301));
    }

    public static MedicalInfoQueryResult parse5302A(ResultBody body) {
        return rowsResult(ChsInfNo.PERSON_OPSP_APPROVAL_QUERY, parseOutputRows(body, ARRAY_KEYS_5302A));
    }

    private static MedicalInfoQueryResult rowsResult(String infno, List<Map<String, Object>> rows) {
        MedicalInfoQueryResult result = new MedicalInfoQueryResult();
        result.setInfno(infno);
        result.setRows(rows);
        return result;
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

    private static List<Map<String, Object>> parseOutputRows(ResultBody body, String... preferredKeys) {
        JSONObject root = parseRootObject(body);
        if (root == null || root.isEmpty()) {
            return List.of();
        }
        if (root.size() == 1) {
            Object only = root.values().iterator().next();
            if (only instanceof JSONArray array) {
                return fromJsonArray(array);
            }
        }
        for (String key : preferredKeys) {
            List<Map<String, Object>> rows = toRowList(root.get(key));
            if (!rows.isEmpty()) {
                return rows;
            }
        }
        for (String key : root.keySet()) {
            Object value = root.get(key);
            if (value instanceof JSONArray array) {
                List<Map<String, Object>> rows = fromJsonArray(array);
                if (!rows.isEmpty()) {
                    return rows;
                }
            }
        }
        if (looksLikeDataRow(root)) {
            return List.of(new HashMap<>(root));
        }
        return List.of();
    }

    private static boolean looksLikeDataRow(JSONObject root) {
        return root.containsKey("mdtrt_id")
            || root.containsKey("mdtrtId")
            || root.containsKey("setl_id")
            || root.containsKey("setlId")
            || root.containsKey("hilist_code")
            || root.containsKey("cum_type_code")
            || root.containsKey("opsp_dise_code")
            || root.containsKey("opspDiseCode")
            || root.containsKey("opsp_dise_name")
            || root.containsKey("opspDiseName")
            || root.containsKey("trtDclaDetlSn")
            || root.containsKey("trt_dcla_detl_sn");
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
