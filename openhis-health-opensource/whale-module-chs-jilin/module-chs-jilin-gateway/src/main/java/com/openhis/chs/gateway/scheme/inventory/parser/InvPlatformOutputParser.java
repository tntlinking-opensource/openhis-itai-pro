/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.inventory.parser;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.chs.gateway.client.ResultBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 进销存平台 output 解析。
 *
 * <p>覆盖 3508–3513 查询行提取，以及 3505/3506 销售上报业务结果判定。</p>
 */
public final class InvPlatformOutputParser {

    private InvPlatformOutputParser() {
    }

    /**
     * 3505/3506 销售上报业务结果（落库 upload_status / platform_ret_rslt / platform_msg）。
     *
     * @param uploadStatus 本地上报状态（SUCCESS / FAILED）
     * @param retRslt      平台业务结果码（无码成功时回填 {@code 1}）
     * @param msgRslt      平台业务消息；可空
     */
    public record SaleUploadBizResult(String uploadStatus, String retRslt, String msgRslt) {
    }

    /**
     * 从平台应答解析查询行列表。
     *
     * <p>吉林省本级常见 {@code output} 为 JSON 数组根（如 {@code [{...}]}），
     * 也兼容对象内嵌数组或单行对象。</p>
     *
     * @param body 平台应答
     * @return 行列表；无数据时为空列表
     */
    public static List<Map<String, Object>> parseQueryRows(ResultBody body) {
        if (body == null || body.getOutput() == null || body.getOutput().isBlank()) {
            return List.of();
        }
        String raw = body.getOutput().trim();
        // 省本级：output 直接是数组
        if (raw.startsWith("[")) {
            List<Map<String, Object>> rows = new ArrayList<>();
            appendJsonArrayRows(rows, JSON.parseArray(raw));
            return rows;
        }
        JSONObject output = JSON.parseObject(raw);
        return parseQueryRows(output);
    }

    /**
     * 从已解析的 output 对象提取行。
     *
     * @param output 对象形态 output
     * @return 行列表
     */
    public static List<Map<String, Object>> parseQueryRows(JSONObject output) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (output == null || output.isEmpty()) {
            return rows;
        }
        // 优先取第一个非空数组节点（data / list / purcinfo 等）
        for (String key : output.keySet()) {
            Object value = output.get(key);
            if (value instanceof JSONArray array) {
                appendJsonArrayRows(rows, array);
                if (!rows.isEmpty()) {
                    return rows;
                }
            }
        }
        // 单行对象回落
        if (output.containsKey("medListCodg") || output.containsKey("med_list_codg")
            || output.containsKey("fixmedinsBchno") || output.containsKey("fixmedins_bchno")
            || output.containsKey("drugTracCodg") || output.containsKey("drug_trac_codg")) {
            rows.add(new HashMap<>(output));
        }
        return rows;
    }

    /**
     * 读取销售类平台字段（camel / snake 兼容）。
     *
     * @param platformResult 平台 result 节点
     * @param camelKey       camelCase 键
     * @param snakeKey       snake_case 键
     * @return 字段值；均无则 null
     */
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

    /**
     * 解析 3505/3506 销售上报业务结果。
     *
     * <p>调用方须已通过 {@code requireSuccess}（{@code infcode=0}）。优先认
     * {@code result.ret_rslt}/{@code retRslt}：{@code 1} 成功、{@code 0} 失败。
     * 吉林省本级常见协议成功但 {@code output} 为空对象且无 {@code ret_rslt}，此时视为业务成功并回填
     * {@code retRslt=1}，避免本地误落 FAILED。</p>
     *
     * @param output 已解析的平台 output（可为空对象）
     * @return 业务结果（含落库状态与业务码）
     */
    public static SaleUploadBizResult resolveSaleUploadBizResult(JSONObject output) {
        JSONObject platformResult = output;
        if (output != null) {
            JSONObject nested = output.getJSONObject("result");
            // 有嵌套 result 时优先读业务结果节点
            if (nested != null) {
                platformResult = nested;
            }
        }
        String retRslt = readSaleUploadField(platformResult, "retRslt", "ret_rslt");
        String msgRslt = readSaleUploadField(platformResult, "msgRslt", "msg_rslt");
        // 显式失败码
        if (ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED.equals(retRslt)) {
            return new SaleUploadBizResult(
                ChsInventoryConstants.UPLOAD_STATUS_FAILED, retRslt, msgRslt);
        }
        // 显式成功码
        if (ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS.equals(retRslt)) {
            return new SaleUploadBizResult(
                ChsInventoryConstants.UPLOAD_STATUS_SUCCESS, retRslt, msgRslt);
        }
        // 无 ret_rslt：省本级空 output，协议层已成功则落 SUCCESS
        if (retRslt == null || retRslt.isBlank()) {
            return new SaleUploadBizResult(
                ChsInventoryConstants.UPLOAD_STATUS_SUCCESS,
                ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS,
                msgRslt);
        }
        // 未知非空码按失败
        return new SaleUploadBizResult(
            ChsInventoryConstants.UPLOAD_STATUS_FAILED, retRslt, msgRslt);
    }

    /**
     * 将 JSON 数组元素追加为 Map 行。
     *
     * @param rows  目标行列表
     * @param array 平台数组
     */
    private static void appendJsonArrayRows(List<Map<String, Object>> rows, JSONArray array) {
        if (array == null || array.isEmpty()) {
            return;
        }
        for (int i = 0; i < array.size(); i++) {
            Object element = array.get(i);
            // 仅对象行入表，跳过标量
            if (element instanceof JSONObject object) {
                rows.add(new HashMap<>(object));
            }
        }
    }
}
