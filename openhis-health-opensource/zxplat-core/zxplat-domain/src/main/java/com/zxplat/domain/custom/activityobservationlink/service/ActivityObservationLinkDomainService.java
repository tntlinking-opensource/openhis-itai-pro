/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.activityobservationlink.service;

import com.zxplat.domain.clinical.observationdefinition.entity.ObservationDefinition;
import com.zxplat.domain.clinical.observationdefinition.service.ObservationDefinitionQueryService;
import com.zxplat.domain.custom.activityobservationlink.command.ActivityObservationLinkItemCommand;
import com.zxplat.domain.custom.activityobservationlink.command.SaveActivityObservationLinksCommand;
import com.zxplat.domain.custom.activityobservationlink.entity.ActivityObservationLink;
import com.zxplat.domain.custom.activityobservationlink.repository.ActivityObservationLinkRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.ObservationDefinitionConstants;
import com.zxplat.domain.share.events.custom.activityobservationlink.ActivityObservationLinksChangedEvent;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 项目 AD ↔ 小项 OD 绑定写侧领域服务（DM-CUS-013）。
 *
 * <p>全量对齐语义：入参集为某项目下的目标全集；缺席旧行软删。跨聚合读经 AD/OD QueryService（B.6.4）。</p>
 */
@Service
@RequiredArgsConstructor
public class ActivityObservationLinkDomainService {

    private final ActivityObservationLinkRepository repository;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ObservationDefinitionQueryService observationDefinitionQueryService;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 全量同步某项目下的小项绑定集（CFG-04）。
     *
     * @param cmd 项目与目标链接全量
     * @return 对齐后仍有效（未软删）的链接行
     */
    public List<ActivityObservationLink> saveLinks(SaveActivityObservationLinksCommand cmd) {
        // 前置校验：项目与租户
        if (cmd == null || cmd.getActivityDefinitionId() == null || cmd.getTenantId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.ACTIVITY_OBSERVATION_LINK_INVALID_ARGUMENT,
                "项目标识与租户不能为空");
        }
        List<ActivityObservationLinkItemCommand> items = cmd.getLinks();
        if (items == null) {
            items = new ArrayList<>();
        }

        // 加载候选：经 ActivityDefinition QS，确认项目存在且未软删（B.6.4 / BR-AOL-01）
        Optional<ActivityDefinition> activityOpt =
            activityDefinitionQueryService.findOptionalActivityDefinitionEntityById(cmd.getActivityDefinitionId());
        if (activityOpt.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.ACTIVITY_OBSERVATION_LINK_ACTIVITY_NOT_FOUND,
                "项目定义不存在，无法绑定小项");
        }
        ActivityDefinition activity = activityOpt.get();
        if (activity.getTypeEnum() == null) {
            throw new BusinessException(
                ErrorCodeConstants.ACTIVITY_OBSERVATION_LINK_ACTIVITY_NOT_FOUND,
                "项目类型未维护，无法绑定小项");
        }

        // 入参小项去重 + 存在性
        Set<Long> seenOdIds = new HashSet<>();
        for (ActivityObservationLinkItemCommand item : items) {
            if (item == null || item.getObservationDefinitionId() == null || item.getSortNum() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.ACTIVITY_OBSERVATION_LINK_INVALID_ARGUMENT,
                    "绑定项须含小项标识与排序号");
            }
            if (!seenOdIds.add(item.getObservationDefinitionId())) {
                throw new BusinessException(
                    ErrorCodeConstants.ACTIVITY_OBSERVATION_LINK_DUPLICATE_OD,
                    "同一项目下小项不可重复绑定");
            }
            // 加载候选：经 OD QS（B.6.4）
            Optional<ObservationDefinition> odOpt =
                observationDefinitionQueryService.findOptionalById(item.getObservationDefinitionId());
            if (odOpt.isEmpty()) {
                throw new BusinessException(
                    ErrorCodeConstants.ACTIVITY_OBSERVATION_LINK_OD_NOT_FOUND,
                    "小项观察定义不存在");
            }
            ObservationDefinition od = odOpt.get();
            if (!ObservationDefinitionConstants.STATUS_ACTIVE.equals(od.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.ACTIVITY_OBSERVATION_LINK_OD_NOT_FOUND,
                    "仅可绑定启用中的小项观察定义");
            }
        }

        // 加载旧行 → diff
        List<ActivityObservationLink> existing =
            repository.findByActivityDefinitionIdAndIsDeletedFalse(cmd.getActivityDefinitionId());
        Map<Long, ActivityObservationLink> existingByOdId = new HashMap<>();
        for (ActivityObservationLink row : existing) {
            existingByOdId.put(row.getObservationDefinitionId(), row);
        }

        Set<Long> keepOdIds = new HashSet<>();
        List<ActivityObservationLink> toSave = new ArrayList<>();

        // upsert 入参行
        for (ActivityObservationLinkItemCommand item : items) {
            keepOdIds.add(item.getObservationDefinitionId());
            ActivityObservationLink row = existingByOdId.get(item.getObservationDefinitionId());
            if (row == null) {
                row = new ActivityObservationLink();
                row.setTenantId(cmd.getTenantId());
                row.setActivityDefinitionId(cmd.getActivityDefinitionId());
                row.setObservationDefinitionId(item.getObservationDefinitionId());
            }
            row.setOrgId(cmd.getOrgId());
            row.setSortNum(item.getSortNum());
            if (item.getRequiredFlag() == null) {
                row.setRequiredFlag(Boolean.TRUE);
            } else {
                row.setRequiredFlag(item.getRequiredFlag());
            }
            row.assertCoreFields();
            toSave.add(row);
        }

        // 软删缺席旧行（BR-AOL-03）
        for (ActivityObservationLink old : existing) {
            if (!keepOdIds.contains(old.getObservationDefinitionId())) {
                old.markDeleted(cmd.getOperatorId());
                toSave.add(old);
            }
        }

        // 持久化
        List<ActivityObservationLink> savedAll = repository.saveAll(toSave);
        List<ActivityObservationLink> active = new ArrayList<>();
        for (ActivityObservationLink row : savedAll) {
            if (!Boolean.TRUE.equals(row.getIsDeleted())) {
                active.add(row);
            }
        }

        // 发布事件
        domainEventPublisher.publish(
            new ActivityObservationLinksChangedEvent(cmd.getActivityDefinitionId(), active.size()));
        return active;
    }
}
