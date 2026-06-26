/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.api.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.domain.medicalinfo.dto.response.MedicalInfoQueryResult;
import com.openhis.chs.domain.medicalinfo.dto.response.Setl5203Result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 5201–5206、5301 平台 output 解析。
 *
 * <p>兼容各省平台 output 节点命名差异（如 {@code mdtrtinfo} vs {@code mdtrt_info}），
 * 统一输出 {@link MedicalInfoQueryResult#getRows()} 供 Port 层映射。</p>
 */
public final class ChsMedicalInfoOutputParser {

    private static final String[] ARRAY_KEYS_5201 = {"mdtrtinfo", "mdtrt_info", "data", "output"};
    private static final String[] ARRAY_KEYS_5202 = {"diseinfo", "dise_info", "data", "output"};
    private static final String[] ARRAY_KEYS_5204 = {"feeListInfo", "fee_list_info", "feedetail", "data", "output"};
    private static final String[] ARRAY_KEYS_5205 = {"feedetail", "fee_detail", "data", "output"};
    private static final String[] ARRAY_KEYS_5206 = {"cuminfo", "cum_info", "data", "output"};
    private static final String[] ARRAY_KEYS_5301 = {"feedetail", "fee_detail", "data", "output"};
    private static final String SETLDETAIL_KEY = "setldetail";

    private ChsMedicalInfoOutputParser() {
    }

    /** 【5201】解析就诊信息列表（mdtrtinfo 数组）。 */
    public static MedicalInfoQueryResult parse5201(ResultBody body, String infno) {
        return rowsResult(infno, parseOutputRows(body, ARRAY_KEYS_5201));
    }

    /** 【5202】解析诊断信息列表（diseinfo 数组）。 */
    public static MedicalInfoQueryResult parse5202(ResultBody body, String infno) {
        return rowsResult(infno, parseOutputRows(body, ARRAY_KEYS_5202));
    }

    /** 【5204】解析费用明细列表（feeListInfo/feedetail 数组）。 */
    public static MedicalInfoQueryResult parse5204(ResultBody body, String infno) {
        return rowsResult(infno, parseOutputRows(body, ARRAY_KEYS_5204));
    }

    /** 【5205】解析门慢门特用药记录（feedetail 数组）。 */
    public static MedicalInfoQueryResult parse5205(ResultBody body, String infno) {
        return rowsResult(infno, parseOutputRows(body, ARRAY_KEYS_5205));
    }

    /** 【5206】解析人员累计信息（cuminfo 数组）。 */
    public static MedicalInfoQueryResult parse5206(ResultBody body, String infno) {
        return rowsResult(infno, parseOutputRows(body, ARRAY_KEYS_5206));
    }

    /** 【5301】解析慢特病备案列表（feedetail/filing 数组，含 opsp_dise_code）。 */
    public static MedicalInfoQueryResult parse5301(ResultBody body, String infno) {
        return rowsResult(infno, parseOutputRows(body, ARRAY_KEYS_5301));
    }

    /**
     * 【5203】解析结算信息：header 为 setlinfo 主表，setlDetails 为基金分项数组。
     *
     * <p>5203 与其他查询不同，返回 {@link Setl5203Result} 而非行列表。</p>
     */
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
            Object value = root.get(key);
            List<Map<String, Object>> rows = toRowList(value);
            if (!rows.isEmpty()) {
                return rows;
            }
        }
        for (String key : root.keySet()) {
            Object value = root.get(key);
            if (value instanceof JSONArray) {
                List<Map<String, Object>> rows = fromJsonArray((JSONArray) value);
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
            || root.containsKey("opspDiseCode");
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
