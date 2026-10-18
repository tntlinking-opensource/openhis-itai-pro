/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.traceassignment.assembler;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.domain.workflow.supplydelivery.queries.model.SupplyDeliveryByRequestModel;
import com.zxplat.domain.workflow.supplyrequest.queries.model.MedicationSummarySlipLineQueryModel;
import com.zxplat.application.inpatient.nursestation.traceassignment.support.ResolveTraceCodesQuery;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 汇总单维度追溯码池装配（**AS-IP-NUR-015 · IF-IP-TRC-02/03/04**）。
 */
@Component
public class SummaryTracePoolAssembler {

    /**
     * 按汇总单号构建码池集合（SupplyDelivery.trace_no 逗号拆分去重）。
     *
     * @param summaryNos          汇总单号列表
     * @param supplyLinesBySummary summaryNo → 汇总 Supply 行
     * @param deliveriesByRequestId requestId → 发放记录
     * @return summaryNo → 码集合
     */
    public Map<String, Set<String>> buildPoolBySummaryNo(
        List<String> summaryNos,
        Map<String, List<MedicationSummarySlipLineQueryModel>> supplyLinesBySummary,
        Map<Long, List<SupplyDeliveryByRequestModel>> deliveriesByRequestId) {
        Map<String, Set<String>> poolBySummary = new LinkedHashMap<>();
        if (summaryNos == null) {
            return poolBySummary;
        }
        for (String summaryNo : summaryNos) {
            if (StringUtils.isEmpty(summaryNo)) {
                continue;
            }
            Set<String> pool = new LinkedHashSet<>();
            List<MedicationSummarySlipLineQueryModel> lines = null;
            if (supplyLinesBySummary != null) {
                lines = supplyLinesBySummary.get(summaryNo);
            }
            if (lines != null) {
                for (MedicationSummarySlipLineQueryModel line : lines) {
                    if (line == null || line.getSupplyRequestId() == null) {
                        continue;
                    }
                    List<SupplyDeliveryByRequestModel> deliveries = null;
                    if (deliveriesByRequestId != null) {
                        deliveries = deliveriesByRequestId.get(line.getSupplyRequestId());
                    }
                    if (deliveries == null) {
                        continue;
                    }
                    for (SupplyDeliveryByRequestModel delivery : deliveries) {
                        this.collectTraceCodesFromDelivery(pool, delivery);
                    }
                }
            }
            poolBySummary.put(summaryNo, pool);
        }
        return poolBySummary;
    }

    /**
     * 构建码池绑定列表，供 {@link com.zxplat.application.inpatient.nursestation.traceassignment.support.PharmacyTraceCodeResolver} 匹配药品。
     *
     * @param supplyLinesBySummary summaryNo → Supply 行
     * @param deliveriesByRequestId requestId → 发放记录
     * @return 码池绑定列表
     */
    public List<ResolveTraceCodesQuery.TraceCodePoolBinding> buildPoolBindings(
        Map<String, List<MedicationSummarySlipLineQueryModel>> supplyLinesBySummary,
        Map<Long, List<SupplyDeliveryByRequestModel>> deliveriesByRequestId) {
        List<ResolveTraceCodesQuery.TraceCodePoolBinding> bindings = new ArrayList<>();
        if (supplyLinesBySummary == null || supplyLinesBySummary.isEmpty()) {
            return bindings;
        }
        Map<String, ResolveTraceCodesQuery.TraceCodePoolBinding> dedupe = new HashMap<>();
        for (Map.Entry<String, List<MedicationSummarySlipLineQueryModel>> entry : supplyLinesBySummary.entrySet()) {
            String summaryNo = entry.getKey();
            List<MedicationSummarySlipLineQueryModel> lines = entry.getValue();
            if (lines == null) {
                continue;
            }
            for (MedicationSummarySlipLineQueryModel line : lines) {
                if (line == null || line.getSupplyRequestId() == null || line.getItemId() == null) {
                    continue;
                }
                List<SupplyDeliveryByRequestModel> deliveries = null;
                if (deliveriesByRequestId != null) {
                    deliveries = deliveriesByRequestId.get(line.getSupplyRequestId());
                }
                if (deliveries == null) {
                    continue;
                }
                for (SupplyDeliveryByRequestModel delivery : deliveries) {
                    this.appendBindingsForDelivery(dedupe, summaryNo, line.getItemId(), delivery);
                }
            }
        }
        bindings.addAll(dedupe.values());
        return bindings;
    }

    /**
     * 断言批量码在快照汇总组涉及的码池内，且不存在跨 summary 歧义（**BR-IP-TRC-005/011**）。
     *
     * @param traceCodes     规范化码列表
     * @param snapshotSummaryNos 组快照涉及的 summaryNo
     * @param poolBySummary  summaryNo → 码池
     * @param notInPoolCode  不在码池错误码
     * @param crossSummaryCode 跨汇总单错误码
     * @param notInPoolMessage 不在码池文案
     * @param crossSummaryMessage 跨汇总单文案
     */
    public void assertCodesInSnapshotSummaries(
        List<String> traceCodes,
        Set<String> snapshotSummaryNos,
        Map<String, Set<String>> poolBySummary,
        String notInPoolCode,
        String crossSummaryCode,
        String notInPoolMessage,
        String crossSummaryMessage) {
        if (traceCodes == null || traceCodes.isEmpty()) {
            return;
        }
        for (String code : traceCodes) {
            if (StringUtils.isEmpty(code)) {
                continue;
            }
            String normalized = code.trim();
            List<String> hitSummaries = new ArrayList<>();
            if (snapshotSummaryNos != null) {
                for (String summaryNo : snapshotSummaryNos) {
                    Set<String> pool = null;
                    if (poolBySummary != null) {
                        pool = poolBySummary.get(summaryNo);
                    }
                    if (pool != null && pool.contains(normalized)) {
                        hitSummaries.add(summaryNo);
                    }
                }
            }
            if (hitSummaries.isEmpty()) {
                throw new BusinessException(notInPoolCode, notInPoolMessage);
            }
            if (hitSummaries.size() > 1) {
                throw new BusinessException(crossSummaryCode, crossSummaryMessage);
            }
        }
    }

    /**
     * 断言提交码均落在对应 summaryNo 码池内（**BR-IP-TRC-005**；IF-IP-TRC-04 保存路径）。
     *
     * @param traceCodesBySummary summaryNo → 待校验码列表
     * @param poolBySummary       summaryNo → 码池
     * @param errorCode           业务错误码
     * @param message             失败文案
     */
    public void assertCodesInPools(
        Map<String, List<String>> traceCodesBySummary,
        Map<String, Set<String>> poolBySummary,
        String errorCode,
        String message) {
        if (traceCodesBySummary == null || traceCodesBySummary.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : traceCodesBySummary.entrySet()) {
            String summaryNo = entry.getKey();
            Set<String> pool = null;
            if (poolBySummary != null) {
                pool = poolBySummary.get(summaryNo);
            }
            List<String> codes = entry.getValue();
            if (codes == null) {
                continue;
            }
            for (String code : codes) {
                if (StringUtils.isEmpty(code)) {
                    continue;
                }
                String normalized = code.trim();
                boolean inPool = false;
                if (pool != null && pool.contains(normalized)) {
                    inPool = true;
                }
                if (!inPool) {
                    throw new BusinessException(errorCode, message);
                }
            }
        }
    }

    /**
     * 码池摘要展示（截断脱敏）。
     *
     * @param pool 码池集合
     * @return 摘要字符串；空池返回空串
     */
    public String buildTracePoolPreview(Set<String> pool) {
        if (pool == null || pool.isEmpty()) {
            return "";
        }
        String joined = String.join(GeneralSharedConstants.COMMA, pool);
        if (joined.length() <= InpatientNurseStationConstants.TRACE_POOL_PREVIEW_MAX_LENGTH) {
            return joined;
        }
        return joined.substring(0, InpatientNurseStationConstants.TRACE_POOL_PREVIEW_MAX_LENGTH) + "...";
    }

    /**
     * 从发放记录拆分追溯码加入码池。
     */
    private void collectTraceCodesFromDelivery(Set<String> pool, SupplyDeliveryByRequestModel delivery) {
        if (pool == null || delivery == null || StringUtils.isEmpty(delivery.getTraceNo())) {
            return;
        }
        for (String part : delivery.getTraceNo().split(GeneralSharedConstants.COMMA)) {
            if (StringUtils.isNotEmpty(part)) {
                pool.add(part.trim());
            }
        }
    }

    /**
     * 为单条发放记录追加码池绑定（去重）。
     */
    private void appendBindingsForDelivery(
        Map<String, ResolveTraceCodesQuery.TraceCodePoolBinding> dedupe,
        String summaryNo,
        Long medicationId,
        SupplyDeliveryByRequestModel delivery) {
        if (dedupe == null || StringUtils.isEmpty(summaryNo) || medicationId == null || delivery == null) {
            return;
        }
        if (StringUtils.isEmpty(delivery.getTraceNo())) {
            return;
        }
        for (String part : delivery.getTraceNo().split(GeneralSharedConstants.COMMA)) {
            if (StringUtils.isEmpty(part)) {
                continue;
            }
            String normalized = part.trim();
            ResolveTraceCodesQuery.TraceCodePoolBinding binding = new ResolveTraceCodesQuery.TraceCodePoolBinding();
            binding.setTraceCode(normalized);
            binding.setSummaryNo(summaryNo);
            binding.setMedicationId(medicationId);
            dedupe.put(summaryNo + ":" + normalized, binding);
        }
    }
}
