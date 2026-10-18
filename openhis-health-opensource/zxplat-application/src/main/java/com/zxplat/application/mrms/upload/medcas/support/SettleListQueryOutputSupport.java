/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.upload.medcas.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.application.mrms.upload.medcas.dto.SettleListQuery4103Dto;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 4103 平台 output → 应用层 DTO 解析。
 *
 * <p>列表摘要须可读：stas_type 映射中文；费用优先 setlinfo.medfee_sumamt，
 * 缺则取 iteminfo 中「金额合计」行或仅分类码行合计（勿把合计行与分类行再加一遍）；
 * 审核意见取 chk_cont。</p>
 */
public final class SettleListQueryOutputSupport {

    /**
     * 禁止实例化：仅静态解析方法。
     */
    private SettleListQueryOutputSupport() {
    }

    /**
     * 从平台 output 解析 setlinfo / iteminfo 并填充摘要字段。
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
        // 详情优先展示平台全量 output（4103 含 setlinfo 及多子表）
        Map<String, Object> fullOutput = new LinkedHashMap<>();
        for (String key : root.keySet()) {
            fullOutput.put(key, root.get(key));
        }
        dto.setPlatformOutput(fullOutput);
        JSONObject setlinfo = extractSetlinfo(root);
        if (setlinfo == null) {
            // 仍可能仅有 iteminfo，补费用合计
            dto.setMedfeeSumamt(sumIteminfoAmt(root));
            return;
        }
        // 详情弹窗兼容：单独保留 setlinfo Map
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
        // 费用：规范字段常空，吉林回包费用在 iteminfo.amt
        BigDecimal medfee = toBigDecimal(setlinfo.get("medfee_sumamt"));
        if (medfee == null) {
            medfee = sumIteminfoAmt(root);
        }
        dto.setMedfeeSumamt(medfee);
        // 状态：保留码给前端 Tag；文案给人看（对齐 4102 重点说明 0～3）
        // 平台偶发数值型 stas_type，统一转字符串再映射，避免文案仍为 "1"
        String stasType = firstNonBlank(
                toPlainString(setlinfo.get("stas_type")),
                toPlainString(setlinfo.get("setl_list_stas")));
        dto.setSetlStatusCode(stasType);
        dto.setSetlStatusText(mapStasTypeLabel(stasType));
        // 审核意见：通过/已提交时常空；不通过时平台写 chk_cont
        if (trimToNull(dto.getPlatformMsg()) == null) {
            dto.setPlatformMsg(trimToNull(setlinfo.getString("chk_cont")));
        }
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
     * 从 iteminfo 解析医疗费总额（作 medfee_sumamt 回落）。
     *
     * <p>吉林 4103 常在末行把 {@code med_chrgitm_type=金额合计} 与分类行并列；
     * 若把全部 {@code amt} 相加会把总额翻倍。优先取合计行；否则只加分类码行。</p>
     *
     * @param root 平台 output 根
     * @return 总额；无可用行则 null
     */
    private static BigDecimal sumIteminfoAmt(JSONObject root) {
        JSONArray items = extractIteminfo(root);
        if (items == null || items.isEmpty()) {
            return null;
        }
        // 优先：平台已给出的「金额合计」行
        for (int i = 0; i < items.size(); i++) {
            JSONObject row = asObject(items.get(i));
            if (row == null) {
                continue;
            }
            if (!isIteminfoTotalRow(row)) {
                continue;
            }
            BigDecimal totalAmt = toBigDecimal(row.get("amt"));
            if (totalAmt != null) {
                return totalAmt;
            }
        }
        // 回落：只合计分类码行（01～14 等），跳过合计/中文标签行
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (int i = 0; i < items.size(); i++) {
            JSONObject row = asObject(items.get(i));
            if (row == null) {
                continue;
            }
            if (!isIteminfoCategoryRow(row)) {
                continue;
            }
            BigDecimal amt = toBigDecimal(row.get("amt"));
            if (amt == null) {
                continue;
            }
            sum = sum.add(amt);
            any = true;
        }
        if (!any) {
            return null;
        }
        return sum;
    }

    /**
     * 是否 iteminfo「金额合计」汇总行（不可再与分类行累加）。
     *
     * @param row iteminfo 行
     * @return true=合计行
     */
    private static boolean isIteminfoTotalRow(JSONObject row) {
        String type = trimToNull(toPlainString(row.get("med_chrgitm_type")));
        if (type == null) {
            return false;
        }
        return type.contains("合计");
    }

    /**
     * 是否收费项目分类明细行（{@code med_chrgitm_type} 为数字码）。
     *
     * @param row iteminfo 行
     * @return true=可参与分类合计
     */
    private static boolean isIteminfoCategoryRow(JSONObject row) {
        String type = trimToNull(toPlainString(row.get("med_chrgitm_type")));
        if (type == null) {
            return false;
        }
        if (isIteminfoTotalRow(row)) {
            return false;
        }
        // 规范码多为 01～14；非纯数字视为非分类行
        for (int i = 0; i < type.length(); i++) {
            char ch = type.charAt(i);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * 提取 iteminfo 数组（根级或 data 下）。
     *
     * @param root output 根
     * @return 数组；无则 null
     */
    private static JSONArray extractIteminfo(JSONObject root) {
        JSONArray direct = asArray(root.get("iteminfo"));
        if (direct != null) {
            return direct;
        }
        JSONObject dataObj = asObject(root.get("data"));
        if (dataObj == null) {
            return null;
        }
        return asArray(dataObj.get("iteminfo"));
    }

    /**
     * 平台 stas_type → 列表展示文案（4102 规范：0 未提交 / 1 已提交 / 2 审核通过 / 3 审核不通过）。
     *
     * @param stasType 平台状态码
     * @return 中文文案；未知码原样回传；空则 null
     */
    private static String mapStasTypeLabel(String stasType) {
        String code = trimToNull(stasType);
        if (code == null) {
            return null;
        }
        if ("0".equals(code)) {
            return "未提交";
        }
        if ("1".equals(code)) {
            return "已提交";
        }
        if ("2".equals(code)) {
            return "审核通过";
        }
        if ("3".equals(code)) {
            return "审核不通过";
        }
        // 地方扩展码：保留原文，避免误译
        return code;
    }

    /**
     * 任意标量转展示用字符串（兼容平台把 stas_type 打成数字）。
     *
     * @param value 原始值
     * @return 去空白；空则 null
     */
    private static String toPlainString(Object value) {
        if (value == null) {
            return null;
        }
        return trimToNull(String.valueOf(value));
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
     * 值转 JSONArray（已是数组或 JSON 数组字符串）。
     *
     * @param value 原始值
     * @return 数组；失败 null
     */
    private static JSONArray asArray(Object value) {
        if (value instanceof JSONArray jsonArray) {
            return jsonArray;
        }
        if (value instanceof String str) {
            String trimmed = trimToNull(str);
            if (trimmed == null) {
                return null;
            }
            try {
                return JSON.parseArray(trimmed);
            } catch (Exception ex) {
                return null;
            }
        }
        if (value instanceof Iterable<?> iterable) {
            JSONArray array = new JSONArray();
            for (Object item : iterable) {
                array.add(item);
            }
            return array;
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
     * 金额转 BigDecimal（仅展示解析，不做业务汇总落库）。
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
