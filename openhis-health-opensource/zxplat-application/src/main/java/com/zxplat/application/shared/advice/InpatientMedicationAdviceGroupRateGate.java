/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.advice;

import com.zxplat.domain.medication.medicationrequest.entity.MedicationRequest;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.zxplat.domain.share.constants.DoctorStationAdviceConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 住院药品成组频次一致性 Gate（AS-IP-DOR-003 · BR-IP-ORD-033）。
 *
 * <p>同 {@code group_id} 多行须共享同一 {@code rate_code}；禁止主药 QD、溶媒目录默认 ST
 * 等异频次并组，否则护士站待执行计数与成组展示口径漂移。</p>
 *
 * <p>落点：{@code application.shared.advice}（与 {@link InpatientEntrustOrderGateSupport} 同包）。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientMedicationAdviceGroupRateGate {

    private final MedicationRequestQueryService medicationRequestQueryService;

    /**
     * 断言批次草稿行内同组频次一致，并与库内既有同组成员对齐。
     *
     * <p>仅检查带 {@code groupId} 的药品行；DELETE 行从既有成员中剔除后再比；
     * 批次内无频次的行跳过，避免空串与已有码误判冲突。</p>
     *
     * @param batchRateByRequestId 本批将落库的 requestId → rateCode（CREATE 可无 id，仅按 group 聚合）
     * @param batchGroupByRequestId 本批 requestId → groupId（UPDATE/DELETE 用；CREATE 可空）
     * @param createGroupRates      本批 CREATE 行：groupId → 该行 rateCode（可多条同组）
     * @param deleteRequestIds      本批 DELETE 的药品 requestId
     * @throws BusinessException 同组出现多于一种非空 rateCode → EX-IP-ORD-308
     */
    public void assertDraftBatchConsistent(
        Map<Long, String> batchRateByRequestId,
        Map<Long, String> batchGroupByRequestId,
        Map<Long, List<String>> createGroupRates,
        Collection<Long> deleteRequestIds) {
        Map<Long, Set<String>> ratesByGroup = new HashMap<>();
        // Pre：批次 CREATE 按组号收集频次
        if (createGroupRates != null) {
            for (Map.Entry<Long, List<String>> entry : createGroupRates.entrySet()) {
                Long groupId = entry.getKey();
                if (groupId == null || entry.getValue() == null) {
                    continue;
                }
                for (String rate : entry.getValue()) {
                    this.addRateIfPresent(ratesByGroup, groupId, rate);
                }
            }
        }
        // Pre：批次 UPDATE 行写入组→频次（组号以本批载荷为准）
        if (batchRateByRequestId != null && batchGroupByRequestId != null) {
            for (Map.Entry<Long, String> entry : batchRateByRequestId.entrySet()) {
                Long requestId = entry.getKey();
                if (requestId == null) {
                    continue;
                }
                if (deleteRequestIds != null && deleteRequestIds.contains(requestId)) {
                    continue;
                }
                String groupRaw = batchGroupByRequestId.get(requestId);
                Long groupId = this.parseGroupId(groupRaw);
                if (groupId == null) {
                    continue;
                }
                this.addRateIfPresent(ratesByGroup, groupId, entry.getValue());
            }
        }
        if (ratesByGroup.isEmpty()) {
            return;
        }
        // Pre：加载库内同组成员，DELETE 剔除后并入频次集合
        Set<Long> deleteSet;
        if (deleteRequestIds == null) {
            deleteSet = Set.of();
        } else {
            deleteSet = new HashSet<>(deleteRequestIds);
        }
        List<MedicationRequest> existing =
            medicationRequestQueryService.listByGroupIds(ratesByGroup.keySet());
        for (MedicationRequest row : existing) {
            if (row == null || row.getId() == null || row.getGroupId() == null) {
                continue;
            }
            if (deleteSet.contains(row.getId())) {
                continue;
            }
            // 本批 UPDATE 已带新频次时，以批次值为准，跳过库内旧值
            if (batchRateByRequestId != null && batchRateByRequestId.containsKey(row.getId())) {
                continue;
            }
            this.addRateIfPresent(ratesByGroup, row.getGroupId(), row.getRateCode());
        }
        this.throwIfAnyGroupHasMultipleRates(ratesByGroup);
    }

    /**
     * 断言并组赋组：待赋组主键集合与目标组既有成员的频次须唯一。
     *
     * @param medicationRequestIds 待写入同一 groupId 的药品申请主键
     * @param groupId              目标组号；空表示拆组，跳过
     * @throws BusinessException 频次多于一种 → EX-IP-ORD-308
     */
    public void assertAssignGroupConsistent(Collection<Long> medicationRequestIds, Long groupId) {
        if (groupId == null || medicationRequestIds == null || medicationRequestIds.isEmpty()) {
            return;
        }
        Map<Long, Set<String>> ratesByGroup = new HashMap<>();
        List<MedicationRequest> assignRows = medicationRequestQueryService.listByIds(medicationRequestIds);
        for (MedicationRequest row : assignRows) {
            if (row == null) {
                continue;
            }
            this.addRateIfPresent(ratesByGroup, groupId, row.getRateCode());
        }
        List<MedicationRequest> existing = medicationRequestQueryService.listByGroupIds(List.of(groupId));
        for (MedicationRequest row : existing) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (medicationRequestIds.contains(row.getId())) {
                continue;
            }
            this.addRateIfPresent(ratesByGroup, groupId, row.getRateCode());
        }
        this.throwIfAnyGroupHasMultipleRates(ratesByGroup);
    }

    /**
     * 将非空频次写入组桶。
     *
     * @param ratesByGroup 组 → 频次集合
     * @param groupId      组号
     * @param rateCode     频次 code；空则忽略
     */
    private void addRateIfPresent(Map<Long, Set<String>> ratesByGroup, Long groupId, String rateCode) {
        if (groupId == null || StringUtils.isEmpty(rateCode)) {
            return;
        }
        String trimmed = rateCode.trim();
        Set<String> bucket = ratesByGroup.get(groupId);
        if (bucket == null) {
            bucket = new HashSet<>();
            ratesByGroup.put(groupId, bucket);
        }
        bucket.add(trimmed);
    }

    /**
     * 解析组号字符串为 Long；非法则空。
     *
     * @param groupRaw 前端/载荷组号
     * @return Long 或 null
     */
    private Long parseGroupId(String groupRaw) {
        if (StringUtils.isEmpty(groupRaw)) {
            return null;
        }
        try {
            return Long.valueOf(groupRaw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 任一组桶 size&gt;1 则拒绝。
     *
     * @param ratesByGroup 组 → 频次集合
     */
    private void throwIfAnyGroupHasMultipleRates(Map<Long, Set<String>> ratesByGroup) {
        if (ratesByGroup == null || ratesByGroup.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, Set<String>> entry : ratesByGroup.entrySet()) {
            Set<String> rates = entry.getValue();
            if (rates == null || rates.size() <= 1) {
                continue;
            }
            // 同组出现 QD/ST 等异码：成组展示与待执行展槽会漂移
            throw new BusinessException(
                DoctorStationAdviceConstants.EX_IP_ORD_308,
                DoctorStationAdviceConstants.MSG_GROUP_RATE_CODE_INCONSISTENT);
        }
    }

    /**
     * 供测试/调用方比较两组码是否语义相同（trim 后 equals）。
     *
     * @param left  左码
     * @param right 右码
     * @return true 表示同码或皆空
     */
    public boolean sameRateCode(String left, String right) {
        String a;
        if (StringUtils.isEmpty(left)) {
            a = "";
        } else {
            a = left.trim();
        }
        String b;
        if (StringUtils.isEmpty(right)) {
            b = "";
        } else {
            b = right.trim();
        }
        return Objects.equals(a, b);
    }
}
