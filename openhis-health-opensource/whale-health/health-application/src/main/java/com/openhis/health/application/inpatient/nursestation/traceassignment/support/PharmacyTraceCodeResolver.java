/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.traceassignment.support;

import java.util.Objects;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.whale.core.application.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 药房追溯码批量解析（**AS-IP-NUR-015 · IF-IP-TRC-03**；药品 + 批号 → 汇总组）。
 *
 * <p>只读内存匹配（B.6.6 §2.2.1 #2）：无 Spring、无跨聚合 I/O；
 * 与门诊发药追溯弹窗同族，供多场景 AppService 静态调用。</p>
 */
public final class PharmacyTraceCodeResolver {

    /**
     * 禁止实例化：仅提供静态解析方法。
     */
    private PharmacyTraceCodeResolver() {
    }

    /**
     * 按药品 + 批号将追溯码匹配到患者待分配汇总组（**BR-IP-TRC-006** 允许共用码）。
     *
     * @param command 码列表、组快照与码池绑定；调用方保证码已规范化
     * @return 组级命中预览与失败明细；入参空时返回空结果
     */
    public static ResolveTraceCodesResult resolveTraceCodes(ResolveTraceCodesQuery command) {
        ResolveTraceCodesResult result = new ResolveTraceCodesResult();
        result.setMatches(new ArrayList<>());
        result.setFailItems(new ArrayList<>());
        if (command == null || command.getTraceCodes() == null || command.getTraceCodes().isEmpty()) {
            return result;
        }
        Map<String, ResolveTraceCodesQuery.TraceCodePoolBinding> bindingByCode = new HashMap<>();
        if (command.getPoolBindings() != null) {
            for (ResolveTraceCodesQuery.TraceCodePoolBinding binding : command.getPoolBindings()) {
                if (binding == null) {
                    continue;
                }
                String normalizedCode = trimToNull(binding.getTraceCode());
                if (normalizedCode == null) {
                    continue;
                }
                bindingByCode.put(normalizedCode, binding);
            }
        }
        Map<String, ResolveTraceCodesQuery.GroupSnapshot> groupByKey = new LinkedHashMap<>();
        if (command.getGroups() != null) {
            for (ResolveTraceCodesQuery.GroupSnapshot group : command.getGroups()) {
                if (group == null) {
                    continue;
                }
                String groupKey = trimToNull(group.getGroupKey());
                if (groupKey == null) {
                    continue;
                }
                groupByKey.put(groupKey, group);
            }
        }
        Map<String, Set<String>> matchedCodesByGroupKey = new LinkedHashMap<>();
        for (String traceCode : command.getTraceCodes()) {
            String normalized = trimToNull(traceCode);
            if (normalized == null) {
                continue;
            }
            ResolveTraceCodesQuery.TraceCodePoolBinding binding = bindingByCode.get(normalized);
            // 码不在药房汇总码池绑定表，标记 NOT_IN_POOL
            if (binding == null) {
                ResolveTraceCodesResult.FailItem failItem = new ResolveTraceCodesResult.FailItem();
                failItem.setTraceCode(normalized);
                failItem.setReasonCode(InpatientNurseStationConstants.TRACE_CODE_FAIL_REASON_NOT_IN_POOL);
                result.getFailItems().add(failItem);
                continue;
            }
            ResolveTraceCodesQuery.GroupSnapshot matchedGroup = findGroupForBinding(groupByKey, binding);
            // 同药多批或批号不符时无法唯一定位汇总组
            if (matchedGroup == null) {
                ResolveTraceCodesResult.FailItem failItem = new ResolveTraceCodesResult.FailItem();
                failItem.setTraceCode(normalized);
                failItem.setReasonCode(InpatientNurseStationConstants.TRACE_CODE_FAIL_REASON_NOT_MATCHED);
                result.getFailItems().add(failItem);
                continue;
            }
            Set<String> codes = matchedCodesByGroupKey.computeIfAbsent(matchedGroup.getGroupKey(), key -> new LinkedHashSet<>());
            codes.add(normalized);
        }
        for (Map.Entry<String, Set<String>> entry : matchedCodesByGroupKey.entrySet()) {
            ResolveTraceCodesQuery.GroupSnapshot group = groupByKey.get(entry.getKey());
            if (group == null) {
                continue;
            }
            ResolveTraceCodesResult.GroupMatch match = new ResolveTraceCodesResult.GroupMatch();
            match.setGroupKey(group.getGroupKey());
            match.setMedicationId(group.getMedicationId());
            match.setLotNumber(group.getLotNumber());
            match.setMatchedTraceCodes(new ArrayList<>(entry.getValue()));
            result.getMatches().add(match);
        }
        return result;
    }

    /**
     * 在组快照中查找与码池绑定一致的 summaryNo + 药品汇总组；同药多批时无法唯一定位则返回 null。
     *
     * @param groupByKey 组键索引
     * @param binding    码池绑定
     * @return 命中组；无唯一匹配时 null
     */
    private static ResolveTraceCodesQuery.GroupSnapshot findGroupForBinding(
        Map<String, ResolveTraceCodesQuery.GroupSnapshot> groupByKey,
        ResolveTraceCodesQuery.TraceCodePoolBinding binding) {
        if (binding == null || groupByKey == null || groupByKey.isEmpty()) {
            return null;
        }
        ResolveTraceCodesQuery.GroupSnapshot singleCandidate = null;
        int candidateCount = 0;
        for (ResolveTraceCodesQuery.GroupSnapshot group : groupByKey.values()) {
            if (group == null) {
                continue;
            }
            if (!Objects.equals(binding.getSummaryNo(), group.getSummaryNo())) {
                continue;
            }
            if (binding.getMedicationId() == null || group.getMedicationId() == null) {
                continue;
            }
            if (!binding.getMedicationId().equals(group.getMedicationId())) {
                continue;
            }
            if (StringUtils.isNotEmpty(binding.getLotNumber())) {
                if (!Objects.equals(binding.getLotNumber(), group.getLotNumber())) {
                    continue;
                }
            }
            singleCandidate = group;
            candidateCount = candidateCount + 1;
        }
        // 同汇总单同药品多批号时，码池侧无批号无法唯一定位，须护士手工确认
        if (candidateCount == 1) {
            return singleCandidate;
        }
        return null;
    }
}
