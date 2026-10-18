/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.assembler;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.domain.share.constants.ChsInventoryConstants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 进销存已落库 {@code request_json} 的追溯码超限拆批（运维补传重放用）。
 *
 * <p>与 {@link InventoryDrugTracChunkAssembler} 同口径：单次 {@code drugtracinfo} ≤
 * {@link ChsInventoryConstants#DRUGTRACINFO_MAX_PER_REQUEST}；批次号追加 {@code -C01}…；
 * {@code cnt} 按码数比例分摊。</p>
 */
public final class InventoryDrugTracJsonChunkAssembler {

    /** 工具类禁止实例化。 */
    private InventoryDrugTracJsonChunkAssembler() {
    }

    /**
     * 将 3502 {@code request_json}（根含 {@code invinfo}）按追溯码上限拆成多份报文。
     *
     * @param requestJson   持久化请求 JSON；可空
     * @param maxPerRequest 单次最大码数；≤0 回落常量
     * @return 拆后报文列表；无需拆或无法解析时返回原串单元素（或空列表）
     */
    public static List<String> split3502RequestJson(String requestJson, int maxPerRequest) {
        // 空报文无法拆批
        if (isEmpty(requestJson)) {
            return List.of();
        }
        int limit = maxPerRequest;
        // 未传上限：回落本系统出站安全上限 100
        if (limit <= 0) {
            limit = ChsInventoryConstants.DRUGTRACINFO_MAX_PER_REQUEST;
        }
        JSONObject root = JSON.parseObject(requestJson);
        // 非法 JSON：原样返回由平台拒收
        if (root == null) {
            return List.of(requestJson);
        }
        JSONObject invinfo = root.getJSONObject("invinfo");
        // 非 3502 包装：无法按 invinfo.drugtracinfo 拆批
        if (invinfo == null) {
            return List.of(requestJson);
        }
        JSONArray drugtracinfo = invinfo.getJSONArray("drugtracinfo");
        // 无码或未超限：原样重放
        if (drugtracinfo == null || drugtracinfo.size() <= limit) {
            return List.of(requestJson);
        }
        String baseBchno = trimToNull(invinfo.getString("fixmedins_bchno"));
        if (baseBchno == null) {
            // 无批次无法生成幂等后缀，保持原报文由平台拒收
            return List.of(requestJson);
        }
        BigDecimal totalCnt = invinfo.getBigDecimal("cnt");
        // cnt 空按 0 分摊，避免 NPE
        if (totalCnt == null) {
            totalCnt = BigDecimal.ZERO;
        }
        int totalCodes = drugtracinfo.size();
        int chunkCount = (totalCodes + limit - 1) / limit;
        List<String> chunks = new ArrayList<>(chunkCount);
        BigDecimal assignedAbs = BigDecimal.ZERO;
        BigDecimal absTotal = totalCnt.abs();
        boolean negative = totalCnt.signum() < 0;
        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            int from = chunkIndex * limit;
            int to = Math.min(from + limit, totalCodes);
            JSONArray slice = new JSONArray();
            for (int i = from; i < to; i++) {
                slice.add(drugtracinfo.get(i));
            }
            JSONObject invCopy = JSON.parseObject(invinfo.toJSONString());
            invCopy.put("drugtracinfo", slice);
            invCopy.put(
                "fixmedins_bchno",
                InventoryDrugTracChunkAssembler.buildChunkFixmedinsBchno(baseBchno, chunkIndex + 1, chunkCount));
            BigDecimal chunkAbs;
            // 数量：按本片码数占比分摊；末片吃尽差额
            if (chunkIndex == chunkCount - 1) {
                chunkAbs = absTotal.subtract(assignedAbs);
            } else {
                chunkAbs = absTotal
                    .multiply(BigDecimal.valueOf(slice.size()))
                    .divide(
                        BigDecimal.valueOf(totalCodes),
                        ChsInventoryConstants.UPLOAD_QUANTITY_SCALE,
                        RoundingMode.HALF_UP);
                assignedAbs = assignedAbs.add(chunkAbs);
            }
            // 出库类 cnt 为负：分摊后保持负号
            if (negative) {
                invCopy.put("cnt", chunkAbs.negate());
            } else {
                // 入库类：正数 cnt
                invCopy.put("cnt", chunkAbs);
            }
            JSONObject rootCopy = new JSONObject();
            rootCopy.put("invinfo", invCopy);
            chunks.add(rootCopy.toJSONString());
        }
        return chunks;
    }

    /**
     * 判断 3502 持久化报文是否超过本系统追溯码条数安全上限（100）。
     *
     * @param requestJson 请求 JSON
     * @return 超限 true
     */
    public static boolean exceedsPlatformDrugTracLimit(String requestJson) {
        // 空报文视为未超限（上层另有空报文跳过）
        if (isEmpty(requestJson)) {
            return false;
        }
        JSONObject root = JSON.parseObject(requestJson);
        if (root == null) {
            return false;
        }
        JSONObject invinfo = root.getJSONObject("invinfo");
        if (invinfo == null) {
            return false;
        }
        JSONArray drugtracinfo = invinfo.getJSONArray("drugtracinfo");
        if (drugtracinfo == null) {
            return false;
        }
        return drugtracinfo.size() > ChsInventoryConstants.DRUGTRACINFO_MAX_PER_REQUEST;
    }

    /**
     * 从拆批后的 3502 报文读取 {@code fixmedins_bchno}（写流水批次列用）。
     *
     * @param chunkRequestJson 拆后报文
     * @return 批次号；解析失败 null
     */
    public static String readFixmedinsBchno(String chunkRequestJson) {
        if (isEmpty(chunkRequestJson)) {
            return null;
        }
        JSONObject root = JSON.parseObject(chunkRequestJson);
        if (root == null) {
            return null;
        }
        JSONObject invinfo = root.getJSONObject("invinfo");
        if (invinfo == null) {
            return null;
        }
        return trimToNull(invinfo.getString("fixmedins_bchno"));
    }
}
