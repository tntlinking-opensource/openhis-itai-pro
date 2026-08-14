/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.medcas.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.health.application.mrms.upload.medcas.dto.SettleListQuery4103Dto;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 4103 平台 output → 应用层 DTO 解析。
 */
public final class SettleListQueryOutputSupport {

    /**
     * 禁止实例化：仅静态解析方法。
     */
    private SettleListQueryOutputSupport() {
    }

    /**
     * 从平台 output 解析 setlinfo 并填充摘要字段。
     *
     * @param dto 待填充结果（已含 success/errMsg）
     * @param output 平台 output 原文
     */
    public static void fillFromOutput(SettleListQuery4103Dto dto, String output) {
        // 空入参直接跳过，避免 NPE
        if (dto == null || trimToNull(output) == null) {
            return;
        }
        JSONObject root;
        try {
            root = JSON.parseObject(output);
        } catch (Exception ex) {
            // 平台原文非 JSON 时保留摘要空字段
            return;
        }
        if (root == null) {
            return;
        }
        JSONObject setlinfo = extractSetlinfo(root);
        if (setlinfo == null) {
            return;
        }
        // 详情弹窗用完整 setlinfo Map
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : setlinfo.keySet()) {
            map.put(key, setlinfo.get(key));
        }
        dto.setSetlinfo(map);
        // 请求未带平台键时，用回包回填 psn_no/setl_id
        if (trimToNull(dto.getPsnNo()) == null) {
            dto.setPsnNo(trimToNull(setlinfo.getString("psn_no")));
        }
        if (trimToNull(dto.getSetlId()) == null) {
            dto.setSetlId(trimToNull(setlinfo.getString("setl_id")));
        }
        dto.setSetlListId(firstNonBlank(
                setlinfo.getString("setl_list_sn"),
                setlinfo.getString("setl_list_id")));
        dto.setMedcasno(trimToNull(setlinfo.getString("medcasno")));
        dto.setPsnName(trimToNull(setlinfo.getString("psn_name")));
        dto.setSetlTime(firstNonBlank(
                setlinfo.getString("dcla_time"),
                setlinfo.getString("setl_end_date"),
                setlinfo.getString("dscg_time")));
        dto.setMedfeeSumamt(toBigDecimal(setlinfo.get("medfee_sumamt")));
        dto.setSetlStatusText(firstNonBlank(
                setlinfo.getString("stas_type"),
                setlinfo.getString("setl_list_stas")));
    }

    /**
     * 提取 setlinfo 节点（兼容根级或 data 包裹）。
     *
     * @param root output 根
     * @return setlinfo；无则 null
     */
    private static JSONObject extractSetlinfo(JSONObject root) {
        Object direct = root.get("setlinfo");
        JSONObject fromDirect = asObject(direct);
        // 常见形态：{setlinfo:{...}}
        if (fromDirect != null) {
            return fromDirect;
        }
        Object data = root.get("data");
        JSONObject dataObj = asObject(data);
        if (dataObj == null) {
            return null;
        }
        // 兼容：{data:{setlinfo:{...}}}
        return asObject(dataObj.get("setlinfo"));
    }

    /**
     * 对象转 JSONObject（已是对象或 JSON 字符串）。
     *
     * @param value 原始值
     * @return 对象；失败 null
     */
    private static JSONObject asObject(Object value) {
        if (value instanceof JSONObject jsonObject) {
            return jsonObject;
        }
        if (value instanceof String str) {
            String trimmed = trimToNull(str);
            if (trimmed == null) {
                return null;
            }
            try {
                return JSON.parseObject(trimmed);
            } catch (Exception ex) {
                return null;
            }
        }
        if (value instanceof Map<?, ?> map) {
            return new JSONObject(map);
        }
        return null;
    }

    /**
     * 取首个非空字符串。
     *
     * @param values 候选
     * @return 非空值或 null
     */
    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    /**
     * 金额转 BigDecimal（仅展示解析，不做业务汇总）。
     *
     * @param value 原始值
     * @return 金额；无法解析则 null
     */
    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        String text = trimToNull(String.valueOf(value));
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (Exception ex) {
            return null;
        }
    }
}
