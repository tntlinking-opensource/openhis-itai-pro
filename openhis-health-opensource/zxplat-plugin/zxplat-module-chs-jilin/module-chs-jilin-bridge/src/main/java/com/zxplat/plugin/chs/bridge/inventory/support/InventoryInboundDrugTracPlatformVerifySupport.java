/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.support;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.domain.inventory.access.Inv35xxQueryInvoker;
import com.zxplat.plugin.chs.domain.inventory.command.InvUploadLineCommand;
import com.zxplat.plugin.chs.domain.inventory.queries.QueryPlatformInvQuery;
import com.zxplat.plugin.chs.domain.inventory.result.PlatformInvQueryResult;
import com.zxplat.plugin.chs.domain.share.constants.ChsInventoryConstants;
import com.zxplat.plugin.chs.domain.share.enums.InvChgType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 入库类 3502 平台 SUCCESS 后的 3512 落码核验（BR-CHS-INV-014）。
 *
 * <p>长春/吉林结算中心对大包 {@code drugtracinfo} 可能回协议成功却静默丢码；
 * 本地若直接记 SUCCESS，后续带同码出库会撞 {@code medinsProdInvChgDBBO_191}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryInboundDrugTracPlatformVerifySupport {

    private final Inv35xxQueryInvoker inv35xxQueryInvoker;

    /**
     * 对「库存增加 + 有追溯码」的 3502 行做 3512 核验；无需核验或已落全时返回 {@code null}。
     *
     * @param contractBusNo 出站契约 busNo
     * @param line          本片上报行（含 invChgType / bchno / drugTracCodgs）
     * @param operatorId    操作人；可空
     * @param operatorName  操作人姓名；可空
     * @return 未落全或查询失败时的失败文案；通过则 null
     */
    public String findIncompleteInboundTraceMessage(
        String contractBusNo,
        InvUploadLineCommand line,
        Long operatorId,
        String operatorName
    ) {
        // 无行命令：无需核验
        if (line == null) {
            return null;
        }
        List<String> expectedCodes = normalizeCodes(line.getDrugTracCodgs());
        return findIncompleteInboundTraceMessage(
            contractBusNo,
            line.getInvChgType(),
            line.getFixmedinsBchno(),
            expectedCodes,
            operatorId,
            operatorName);
    }

    /**
     * 对已落库 3502 {@code request_json} 做同样核验（补传重放路径）。
     *
     * @param contractBusNo 出站契约 busNo
     * @param requestJson   3502 报文 JSON
     * @param operatorId    操作人；可空
     * @param operatorName  操作人姓名；可空
     * @return 未落全或查询失败时的失败文案；通过/无需核验则 null
     */
    public String findIncompleteInboundTraceMessageFromRequestJson(
        String contractBusNo,
        String requestJson,
        Long operatorId,
        String operatorName
    ) {
        // 无持久化报文：补传路径无法核验
        if (isEmpty(requestJson)) {
            return null;
        }
        JSONObject root = JSON.parseObject(requestJson);
        // 报文非法 JSON：视为无法核验（调用方不应记 SUCCESS）
        if (root == null) {
            return null;
        }
        JSONObject invinfo = root.getJSONObject("invinfo");
        // 部分存量仅存 invinfo 本体，无根包装
        if (invinfo == null) {
            invinfo = root;
        }
        String invChgType = invinfo.getString("inv_chg_type");
        String bchno = invinfo.getString("fixmedins_bchno");
        List<String> expectedCodes = extractCodesFromDrugtracinfo(invinfo.getJSONArray("drugtracinfo"));
        return findIncompleteInboundTraceMessage(
            contractBusNo, invChgType, bchno, expectedCodes, operatorId, operatorName);
    }

    /**
     * 按变更类型 + 期望码列表查 3512，比对本批次落码是否齐全。
     *
     * @param contractBusNo 契约 busNo
     * @param invChgType    3502 inv_chg_type
     * @param fixmedinsBchno 本片批次号
     * @param expectedCodes 本片期望追溯码（已 trim）
     * @param operatorId    操作人
     * @param operatorName  姓名
     * @return 失败文案或 null
     */
    public String findIncompleteInboundTraceMessage(
        String contractBusNo,
        String invChgType,
        String fixmedinsBchno,
        List<String> expectedCodes,
        Long operatorId,
        String operatorName
    ) {
        // Pre：仅入库类且本片带码才核验；出库/无码不查 3512
        if (!requiresInboundTraceVerify(invChgType, expectedCodes)) {
            return null;
        }
        // Gate：缺契约或批次号无法按批次查 3512，须记失败文案而非放行
        if (isEmpty(contractBusNo) || isEmpty(fixmedinsBchno)) {
            return ChsInventoryConstants.PLATFORM_MSG_INBOUND_TRACE_VERIFY_INCOMPLETE_PREFIX
                + "：缺少契约或批次号，无法核验";
        }
        Set<String> expected = new LinkedHashSet<>(expectedCodes);
        try {
            QueryPlatformInvQuery query = new QueryPlatformInvQuery()
                .setContractBusNo(contractBusNo)
                .setFixmedinsBchno(fixmedinsBchno.trim())
                .setOperatorId(operatorId)
                .setOperatorName(operatorName);
            PlatformInvQueryResult result = inv35xxQueryInvoker.query3512(query);
            Set<String> platformCodes = collectPlatformDrugTracCodes(result);
            List<String> missing = new ArrayList<>();
            // 比对本片期望码是否全部出现在本批次 3512
            for (String code : expected) {
                if (!platformCodes.contains(code)) {
                    missing.add(code);
                }
            }
            // Gate：有任一期望码未出现在本批次 3512 → 不得记本地 SUCCESS
            if (missing.isEmpty()) {
                return null;
            }
            String sample = missing.get(0);
            // 多样例时文案加「等」，避免过长
            if (missing.size() > 1) {
                sample = sample + " 等";
            }
            return ChsInventoryConstants.PLATFORM_MSG_INBOUND_TRACE_VERIFY_INCOMPLETE_PREFIX
                + "：批次=" + fixmedinsBchno.trim()
                + " 期望=" + expected.size()
                + " 平台=" + platformCodes.size()
                + " 缺失=" + missing.size()
                + " 样例=" + sample
                + "；请缩小拆批后重传入库，禁止去码冒充成功";
        } catch (RuntimeException ex) {
            // 3512 查询异常同样不得放行 SUCCESS（可能已静默丢码）
            log.warn(
                "3512 入库追溯核验失败 busNo={} bchno={} msg={}",
                contractBusNo,
                fixmedinsBchno,
                ex.getMessage());
            return ChsInventoryConstants.PLATFORM_MSG_INBOUND_TRACE_VERIFY_INCOMPLETE_PREFIX
                + "：3512查询异常 " + ex.getMessage();
        }
    }

    /**
     * 是否需要对本次 3502 做入库追溯核验。
     *
     * @param invChgType    变更类型 code
     * @param expectedCodes 本片码列表
     * @return 入库类且有码时 true
     */
    public static boolean requiresInboundTraceVerify(String invChgType, List<String> expectedCodes) {
        // 无码：平台不会落 drugtrac，跳过核验
        if (expectedCodes == null || expectedCodes.isEmpty()) {
            return false;
        }
        InvChgType type = InvChgType.fromCodeOptional(invChgType);
        // 未知变更类型：保守跳过（避免误拦出库）
        if (type == null) {
            return false;
        }
        // 仅库存增加类（采购入库/其他入库/盘盈等）需要防虚空入库
        return type.isStockIncrease();
    }

    /**
     * 从 3512 行 Map 收集追溯码（兼容驼峰/蛇形键）。
     *
     * @param result 3512 查询结果；可空
     * @return 平台码集合
     */
    static Set<String> collectPlatformDrugTracCodes(PlatformInvQueryResult result) {
        Set<String> codes = new LinkedHashSet<>();
        // 空结果按平台无码处理
        if (result == null || result.getRows() == null) {
            return codes;
        }
        for (Map<String, Object> row : result.getRows()) {
            String code = readDrugTracCodg(row);
            if (isNotEmpty(code)) {
                codes.add(code.trim());
            }
        }
        return codes;
    }

    /**
     * 从平台行读取追溯码字段。
     *
     * @param row 3512 行；可空
     * @return 码串或 null
     */
    private static String readDrugTracCodg(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        // 优先驼峰（网关部分解析器）
        Object camel = row.get("drugTracCodg");
        if (camel != null && isNotEmpty(String.valueOf(camel))) {
            return String.valueOf(camel).trim();
        }
        // 回落蛇形（平台原生字段名）
        Object snake = row.get("drug_trac_codg");
        if (snake != null && isNotEmpty(String.valueOf(snake))) {
            return String.valueOf(snake).trim();
        }
        return null;
    }

    /**
     * 规范化命令侧码列表（trim、去空）。
     *
     * @param raw 原始列表；可空
     * @return 非空码列表
     */
    static List<String> normalizeCodes(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String code : raw) {
            if (isEmpty(code)) {
                continue;
            }
            String trimmed = code.trim();
            if (isEmpty(trimmed)) {
                continue;
            }
            out.add(trimmed);
        }
        return out;
    }

    /**
     * 从报文 drugtracinfo 数组提取码。
     *
     * @param drugtracinfo JSON 数组；可空
     * @return 码列表
     */
    static List<String> extractCodesFromDrugtracinfo(JSONArray drugtracinfo) {
        List<String> out = new ArrayList<>();
        if (drugtracinfo == null || drugtracinfo.isEmpty()) {
            return out;
        }
        for (int i = 0; i < drugtracinfo.size(); i++) {
            JSONObject item = drugtracinfo.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String code = item.getString("drug_trac_codg");
            // 兼容驼峰键（手工补传偶发）
            if (isEmpty(code)) {
                code = item.getString("drugTracCodg");
            }
            if (isEmpty(code)) {
                continue;
            }
            out.add(code.trim());
        }
        return out;
    }
}
