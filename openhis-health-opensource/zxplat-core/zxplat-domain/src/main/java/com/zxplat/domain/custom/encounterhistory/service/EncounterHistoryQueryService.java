/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.encounterhistory.service;

import com.zxplat.domain.share.constants.CustomConstants;
import com.zxplat.domain.custom.encounterhistory.entity.EncounterHistory;
import com.zxplat.domain.custom.encounterhistory.queries.model.DischargeWayQueryModel;
import com.zxplat.domain.custom.encounterhistory.queries.model.EncounterHistoryMilestoneQueryModel;
import com.zxplat.domain.custom.encounterhistory.queries.model.LatestTransferContextQueryModel;
import com.zxplat.domain.custom.encounterhistory.repository.EncounterHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

/**
 * 就诊历史（里程碑事件）读侧查询服务。
 *
 * <p>职责：按就诊列出转科、出院等里程碑时间线，并解析最近一次转科上下文与出院方式。</p>
 *
 * <p>边界：只读；无转科/出院记录时返回 {@code null} 或空列表。</p>
 *
 * <p>聚合边界：仅本聚合 {@link EncounterHistoryRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class EncounterHistoryQueryService {

    private final EncounterHistoryRepository repository;

    /**
     * 按就诊列出全部里程碑记录（创建时间升序，即时间线顺序）。
     *
     * @param encounterId 就诊标识
     * @return 里程碑视图列表
     */
    public List<EncounterHistoryMilestoneQueryModel> listMilestonesByEncounter(Long encounterId) {
        List<EncounterHistory> rows = repository.findAllByEncounterIdOrderByCreatedAtAsc(encounterId);
        List<EncounterHistoryMilestoneQueryModel> result = new ArrayList<>();
        for (EncounterHistory row : rows) {
            result.add(toMilestoneModel(row));
        }
        return result;
    }

    /**
     * 解析该就诊最近一次「转科」里程碑上的来源/目标科室与位置（用于展示当前流转上下文）。
     *
     * @param encounterId 就诊标识
     * @return 最近转科上下文；无转科记录时 {@code null}
     */
    public LatestTransferContextQueryModel findLatestTransferContext(Long encounterId) {
        Optional<EncounterHistory> optional = repository.findFirstByEncounterIdAndNameOrderByCreatedAtDesc(
            encounterId,
            CustomConstants.EncounterHistoryMilestoneName.TRANSFER
        );
        if (optional.isEmpty()) {
            return null;
        }
        EncounterHistory latest = optional.get();
        LatestTransferContextQueryModel model = new LatestTransferContextQueryModel();
        model.setEncounterHistoryId(latest.getId());
        model.setEncounterId(latest.getEncounterId());
        model.setOriginalOrganizationId(latest.getOriginalOrganizationId());
        model.setTargetOrganizationId(latest.getTargetOrganizationId());
        model.setOriginalLocationId(latest.getOriginalLocationId());
        model.setTargetLocationId(latest.getTargetLocationId());
        model.setCreatedAt(latest.getCreatedAt());
        return model;
    }

    /**
     * 解析该就诊最近一次「出院」里程碑上的离院方式编码（列表按创建时间倒序取首条）。
     *
     * @param encounterId 就诊标识
     * @return 出院方式读模型；无出院记录时 {@code null}
     */
    public DischargeWayQueryModel resolveDischargeWay(Long encounterId) {
        List<EncounterHistory> discharges = repository.findAllByEncounterIdAndNameOrderByCreatedAtDesc(
            encounterId,
            CustomConstants.EncounterHistoryMilestoneName.DISCHARGE
        );
        if (discharges.isEmpty()) {
            return null;
        }
        EncounterHistory latest = discharges.get(0);
        DischargeWayQueryModel model = new DischargeWayQueryModel();
        model.setEncounterHistoryId(latest.getId());
        model.setOutWayCode(latest.getOutWayCode());
        return model;
    }

    /**
     * 按就诊主键批量加载历史实体（住院 ON_HOLD 路由字段回填）。
     *
     * @param encounterIds 就诊主键集合
     * @return 历史实体列表
     */
    public List<EncounterHistory> listEncounterHistoryEntitiesByEncounterIds(Collection<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return Collections.emptyList();
        }
        return repository.findAllByEncounterIdIn(distinct);
    }

    /**
     * 里程碑实体映射为时间线读模型。
     *
     * @param row 历史记录实体
     * @return 里程碑视图
     */
    private EncounterHistoryMilestoneQueryModel toMilestoneModel(EncounterHistory row) {
        EncounterHistoryMilestoneQueryModel model = new EncounterHistoryMilestoneQueryModel();
        model.setId(row.getId());
        model.setName(row.getName());
        model.setEncounterId(row.getEncounterId());
        model.setRequestId(row.getRequestId());
        model.setRequestTableName(row.getRequestTableName());
        model.setOriginalOrganizationId(row.getOriginalOrganizationId());
        model.setTargetOrganizationId(row.getTargetOrganizationId());
        model.setOriginalLocationId(row.getOriginalLocationId());
        model.setTargetLocationId(row.getTargetLocationId());
        model.setOutWayCode(row.getOutWayCode());
        model.setCreatedAt(row.getCreatedAt());
        return model;
    }
}
