/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.activityobservationlink.service;

import com.zxplat.domain.custom.activityobservationlink.entity.ActivityObservationLink;
import com.zxplat.domain.custom.activityobservationlink.queries.model.ActivityObservationLinkModel;
import com.zxplat.domain.custom.activityobservationlink.repository.ActivityObservationLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 项目↔小项绑定读侧查询服务（DM-CUS-013）。
 *
 * <p>仅本聚合 {@link ActivityObservationLinkRepository}。
 * {@code odDisplay}/{@code odCode} 留空，由 AppService 调 OD QueryService enrich（B.6.4）。</p>
 */
@Service
@RequiredArgsConstructor
public class ActivityObservationLinkQueryService {

    private final ActivityObservationLinkRepository repository;

    /**
     * 列出某项目下未软删绑定，按 sortNum、id 排序。
     *
     * @param activityId 项目 ActivityDefinition 主键
     * @return 绑定读模型（无 OD 展示字段）
     */
    public List<ActivityObservationLinkModel> listByActivityId(Long activityId) {
        // activityId + 软删 → 按 sortNum 排序（OD 展示由应用层 enrich）
        if (activityId == null) {
            return new ArrayList<>();
        }
        List<ActivityObservationLink> rows =
            repository.findByActivityDefinitionIdAndIsDeletedFalse(activityId);
        List<ActivityObservationLinkModel> models = new ArrayList<>();
        for (ActivityObservationLink row : rows) {
            models.add(toModel(row));
        }
        models.sort(Comparator
            .comparing(ActivityObservationLinkModel::getSortNum, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(ActivityObservationLinkModel::getId, Comparator.nullsLast(Long::compareTo)));
        return models;
    }

    /**
     * 统计各小项被多少个未软删项目引用（同一 AD 多次绑定同一 OD 只计 1）。
     *
     * @param observationDefinitionIds 小项主键集合
     * @return 小项 id → 引用项目数；无引用时不写入该键
     */
    public Map<Long, Integer> countDistinctActivitiesByObservationDefinitionIds(
            Collection<Long> observationDefinitionIds) {
        Map<Long, Integer> result = new HashMap<>();
        if (observationDefinitionIds == null || observationDefinitionIds.isEmpty()) {
            return result;
        }
        Set<Long> uniqueOdIds = new HashSet<>();
        for (Long odId : observationDefinitionIds) {
            if (odId != null) {
                uniqueOdIds.add(odId);
            }
        }
        // 无有效小项主键：直接空结果
        if (uniqueOdIds.isEmpty()) {
            return result;
        }
        List<ActivityObservationLink> rows =
            repository.findByObservationDefinitionIdInAndIsDeletedFalse(uniqueOdIds);
        Map<Long, Set<Long>> activityIdsByOdId = new HashMap<>();
        for (ActivityObservationLink row : rows) {
            if (row == null || row.getObservationDefinitionId() == null || row.getActivityDefinitionId() == null) {
                continue;
            }
            activityIdsByOdId
                .computeIfAbsent(row.getObservationDefinitionId(), k -> new HashSet<>())
                .add(row.getActivityDefinitionId());
        }
        for (Map.Entry<Long, Set<Long>> entry : activityIdsByOdId.entrySet()) {
            result.put(entry.getKey(), entry.getValue().size());
        }
        return result;
    }

    /**
     * 按项目主键批量列出未软删绑定，并按 sortNum、id 排序后分组。
     *
     * @param activityIds 项目 AD 主键集合
     * @return activityId → 绑定读模型列表（无 OD 展示字段）
     */
    public Map<Long, List<ActivityObservationLinkModel>> mapByActivityIds(Collection<Long> activityIds) {
        Map<Long, List<ActivityObservationLinkModel>> result = new HashMap<>();
        if (activityIds == null || activityIds.isEmpty()) {
            return result;
        }
        Set<Long> uniqueIds = new HashSet<>();
        for (Long activityId : activityIds) {
            if (activityId != null) {
                uniqueIds.add(activityId);
            }
        }
        // 无有效项目主键：直接空结果
        if (uniqueIds.isEmpty()) {
            return result;
        }
        List<ActivityObservationLink> rows =
            repository.findByActivityDefinitionIdInAndIsDeletedFalse(uniqueIds);
        for (ActivityObservationLink row : rows) {
            if (row == null || row.getActivityDefinitionId() == null) {
                continue;
            }
            result.computeIfAbsent(row.getActivityDefinitionId(), k -> new ArrayList<>()).add(toModel(row));
        }
        for (List<ActivityObservationLinkModel> models : result.values()) {
            models.sort(Comparator
                .comparing(ActivityObservationLinkModel::getSortNum, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(ActivityObservationLinkModel::getId, Comparator.nullsLast(Long::compareTo)));
        }
        return result;
    }

    /**
     * 实体转读模型（不含跨聚合 OD 展示字段）。
     *
     * @param row 绑定实体
     * @return 读模型
     */
    private ActivityObservationLinkModel toModel(ActivityObservationLink row) {
        ActivityObservationLinkModel model = new ActivityObservationLinkModel();
        model.setId(row.getId());
        model.setActivityDefinitionId(row.getActivityDefinitionId());
        model.setObservationDefinitionId(row.getObservationDefinitionId());
        model.setSortNum(row.getSortNum());
        model.setRequiredFlag(row.getRequiredFlag());
        return model;
    }
}
