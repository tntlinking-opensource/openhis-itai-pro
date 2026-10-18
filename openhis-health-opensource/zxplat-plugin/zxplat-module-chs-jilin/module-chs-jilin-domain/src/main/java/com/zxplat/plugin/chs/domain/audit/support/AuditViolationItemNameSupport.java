/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.audit.support;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.plugin.chs.domain.audit.result.AuditAnalyzeResult;
import com.zxplat.plugin.chs.domain.audit.result.AuditViolationDetailResult;
import com.zxplat.plugin.chs.domain.audit.result.AuditViolationResult;
import com.zxplat.plugin.chs.domain.audit.snapshot.AuditFeeOrderLine;
import com.zxplat.plugin.chs.domain.audit.snapshot.AuditFeeSnapshot;
import com.zxplat.plugin.chs.domain.share.constants.ChsDetailAuditConstants;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 3101 违规明细回填院内药品/项目名称。
 *
 * <p>平台 {@code judge_result_detail_dtos} 仅有 {@code rx_id}，无品名；
 * 用 Snapshot 医嘱行按 {@code rx_id}（费用流水）或 {@code rxno}（处方/医嘱号）匹配。</p>
 */
public final class AuditViolationItemNameSupport {

    private AuditViolationItemNameSupport() {
    }

    /**
     * 按 Snapshot 医嘱行回填 {@link AuditViolationDetailResult#setItemName(String)}。
     *
     * @param result 3101 分析结果（可空）
     * @param snap   本次出站 Snapshot（可空）
     */
    public static void enrichItemNames(AuditAnalyzeResult result, AuditFeeSnapshot snap) {
        if (result == null || result.getViolations() == null || result.getViolations().isEmpty()) {
            return;
        }
        if (snap == null || snap.getOrders() == null || snap.getOrders().isEmpty()) {
            return;
        }
        Map<String, String> nameByRxId = new HashMap<>();
        Map<String, List<AuditFeeOrderLine>> ordersByRxno = new HashMap<>();
        for (AuditFeeOrderLine order : snap.getOrders()) {
            if (order == null) {
                continue;
            }
            String name = normalizeName(order.getHilistName());
            if (isEmpty(name)) {
                continue;
            }
            if (isNotEmpty(order.getRxId())) {
                nameByRxId.put(order.getRxId().trim(), name);
            }
            if (isNotEmpty(order.getRxno())) {
                ordersByRxno
                    .computeIfAbsent(order.getRxno().trim(), key -> new ArrayList<>())
                    .add(order);
            }
        }
        if (nameByRxId.isEmpty() && ordersByRxno.isEmpty()) {
            return;
        }
        for (AuditViolationResult violation : result.getViolations()) {
            if (violation == null || violation.getDetails() == null) {
                continue;
            }
            for (AuditViolationDetailResult detail : violation.getDetails()) {
                if (detail == null || isNotEmpty(detail.getItemName())) {
                    continue;
                }
                String rxKey = isNotEmpty(detail.getRxId()) ? detail.getRxId().trim() : null;
                if (isEmpty(rxKey)) {
                    continue;
                }
                String name = nameByRxId.get(rxKey);
                if (isEmpty(name)) {
                    name = resolveNameByRxno(ordersByRxno.get(rxKey), detail.getVolaAmt());
                }
                if (isNotEmpty(name)) {
                    detail.setItemName(name);
                }
            }
        }
    }

    private static String normalizeName(String raw) {
        if (isEmpty(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || ChsDetailAuditConstants.DEFAULT_SPEC.equals(trimmed)) {
            return null;
        }
        return trimmed;
    }

    /**
     * 平台常把处方号回写到 {@code rx_id}：同号多行时优先金额匹配，否则拼接去重品名。
     */
    private static String resolveNameByRxno(List<AuditFeeOrderLine> candidates, BigDecimal volaAmt) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (volaAmt != null) {
            for (AuditFeeOrderLine order : candidates) {
                if (order == null || order.getSumamt() == null) {
                    continue;
                }
                if (order.getSumamt().compareTo(volaAmt) == 0) {
                    String matched = normalizeName(order.getHilistName());
                    if (isNotEmpty(matched)) {
                        return matched;
                    }
                }
            }
        }
        Set<String> names = new LinkedHashSet<>();
        for (AuditFeeOrderLine order : candidates) {
            if (order == null) {
                continue;
            }
            String name = normalizeName(order.getHilistName());
            if (isNotEmpty(name)) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            return null;
        }
        return String.join("、", names);
    }
}
