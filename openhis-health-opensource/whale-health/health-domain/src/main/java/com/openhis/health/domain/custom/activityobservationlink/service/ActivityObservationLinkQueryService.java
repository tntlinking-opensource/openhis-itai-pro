/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.activityobservationlink.service;

import com.openhis.health.domain.custom.activityobservationlink.entity.ActivityObservationLink;
import com.openhis.health.domain.custom.activityobservationlink.queries.model.ActivityObservationLinkModel;
import com.openhis.health.domain.custom.activityobservationlink.repository.ActivityObservationLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
