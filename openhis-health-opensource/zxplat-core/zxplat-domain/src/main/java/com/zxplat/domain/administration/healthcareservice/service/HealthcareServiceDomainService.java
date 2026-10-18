/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.healthcareservice.service;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.domain.share.constants.BusinessSequenceConstants;
import com.zxplat.domain.administration.healthcareservice.command.*;
import com.zxplat.domain.administration.healthcareservice.entity.HealthcareService;
import com.zxplat.domain.administration.healthcareservice.repository.HealthcareServiceRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.events.administration.healthcareservice.*;
import com.zxplat.domain.share.utils.CommandBatchUtil;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 医疗服务领域服务，承载医疗服务（HealthcareService）的写侧编排能力。
 *
 * <p>管理医疗服务的创建、发布、修改、停用、重新发布和废弃全生命周期。
 * 所有写操作均遵循「先落库后发事件」时序（通用规范 B.14 硬门禁）。</p>
 *
 * <p>对应 DM-ADM-007 §6.1 写侧方法集；仅注入本聚合 {@link HealthcareServiceRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class HealthcareServiceDomainService {
    private final HealthcareServiceRepository repository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 医疗服务 busNo：主数据全局递增，前缀 {@link AggregateTypeConstants#HEALTHCARE_SERVICE}。 */
    private final BusinessSequenceService businessSequenceService;


    /**
     * 创建医疗服务：以草稿状态初始化并批量落库，发布服务创建事件。
     *
     * @param cmd 创建医疗服务命令，包含一条或多条创建项
     * @return 首条新建医疗服务的主键
     * @throws BusinessException 创建项列表为空（HEALTHCARE_SERVICE_INVALID_ARGUMENT）
     */
    public Long createService(CreateHealthcareServiceCommand cmd) {
        List<CreateHealthcareServiceCommand.Item> items = cmd.resolveItems();
        // 入参列表为空，本次命令无医疗服务条目，无法创建，直接拒绝。
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_ARGUMENT, "创建医疗服务入参不能为空");
        }
        List<String> busNos = businessSequenceService.getSeq(AggregateTypeConstants.HEALTHCARE_SERVICE, BusinessSequenceConstants.MASTER_DATA_2_PREFIX_SEQ_LENGTH, items.size());
        // 逐条构建医疗服务实体，初始状态为草稿（DRAFT），尚不对外生效。
        List<HealthcareService> toSave = new ArrayList<>();
        int busIdx = 0;
        for (CreateHealthcareServiceCommand.Item item : items) {
            HealthcareService row = new HealthcareService();
            row.setBusNo(busNos.get(busIdx));
            busIdx++;
            row.setName(item.getName());
            row.setTypeCode(item.getTypeCode());
            row.setCategoryCode(item.getCategoryCode());
            row.setOfferedOrgId(item.getOfferedOrgId());
            // Gate：诊查费绑定串格式（空=零元可绑；非空须正整数 CSV）
            row.assertActivityIdsBindingAllowed(item.getActivityIds());
            row.setActivityIds(this.normalizeActivityIdsCsv(item.getActivityIds()));
            row.initDraft();
            toSave.add(row);
        }
        // 先批量落库，确保数据持久化后再发布领域事件（B.14 硬门禁）。
        List<HealthcareService> saved = repository.saveAll(toSave);
        // 逐条发布医疗服务创建领域事件，通知下游读模型进行数据同步。
        for (HealthcareService row : saved) {
            domainEventPublisher.publish(new HealthcareServiceCreatedEvent(
                row.getId(), row.getName(), row.getCategoryCode(), row.getTypeCode(), row.getOfferedOrgId()));
        }
        return saved.get(0).getId();
    }

    /**
     * 发布医疗服务：校验草稿状态后迁移至 ACTIVE，并发布服务发布事件。
     *
     * <p>只有草稿状态的服务可以发布；发布后服务对外生效，可被患者/系统引用。</p>
     *
     * @param cmd 发布医疗服务命令
     * @throws BusinessException 服务不存在或当前状态不允许发布
     */
    public void publishService(PublishHealthcareServiceCommand cmd) {
        List<PublishHealthcareServiceCommand.Item> items = cmd.resolveItems();
        // 批量加载待发布的医疗服务实体，确认记录存在且有效。
        Map<Long, HealthcareService> map = this.loadMap(CommandBatchUtil.extractIds(items, PublishHealthcareServiceCommand.Item::getServiceId));
        List<HealthcareService> toSave = new ArrayList<>();
        for (PublishHealthcareServiceCommand.Item item : items) {
            HealthcareService row = require(map, item.getServiceId());
            // 校验当前状态允许发布（仅草稿可发布），并再次校验诊查费绑定串格式。
            row.assertPublishAllowed();
            row.assertActivityIdsBindingAllowed(row.getActivityIds());
            row.transitToActive();
            toSave.add(row);
        }
        // 先批量落库，确保状态变更持久化后再发布事件（B.14 硬门禁）。
        repository.saveAll(toSave);
        // 逐条发布医疗服务上线领域事件，通知下游挂号/预约等模块该服务已生效。
        for (PublishHealthcareServiceCommand.Item item : items) {
            HealthcareService row = require(map, item.getServiceId());
            domainEventPublisher.publish(new HealthcareServicePublishedEvent(row.getId(), row.getName()));
        }
    }

    /**
     * 修改医疗服务信息：更新名称、类型、分类、所属机构、诊查费绑定，并发布服务修改事件。
     *
     * <p>草稿和已发布状态的服务均可修改；ACTIVE 态禁止改类别/类型（BR-HCS-008）。</p>
     *
     * @param cmd 修改医疗服务命令
     * @throws BusinessException 服务不存在、状态不允许或绑定串非法
     */
    public void modifyService(ModifyHealthcareServiceCommand cmd) {
        List<ModifyHealthcareServiceCommand.Item> items = cmd.resolveItems();
        // 批量加载待修改的医疗服务实体。
        Map<Long, HealthcareService> map = this.loadMap(CommandBatchUtil.extractIds(items, ModifyHealthcareServiceCommand.Item::getServiceId));
        List<HealthcareService> toSave = new ArrayList<>();
        for (ModifyHealthcareServiceCommand.Item item : items) {
            HealthcareService row = require(map, item.getServiceId());
            // 校验可改态、ACTIVE 类别/类型不可变、诊查费绑定串格式。
            row.assertModifiable();
            row.assertActiveImmutableCategoryAndType(item.getCategoryCode(), item.getTypeCode());
            row.assertActivityIdsBindingAllowed(item.getActivityIds());
            row.setName(item.getName());
            row.setTypeCode(item.getTypeCode());
            row.setCategoryCode(item.getCategoryCode());
            row.setOfferedOrgId(item.getOfferedOrgId());
            row.setActivityIds(this.normalizeActivityIdsCsv(item.getActivityIds()));
            toSave.add(row);
        }
        // 先批量落库，确保字段变更持久化后再发布事件（B.14 硬门禁）。
        repository.saveAll(toSave);
        // 逐条发布医疗服务信息修改领域事件，携带变更字段摘要供下游同步。
        for (ModifyHealthcareServiceCommand.Item item : items) {
            domainEventPublisher.publish(new HealthcareServiceModifiedEvent(item.getServiceId(), item.getChangedFields()));
        }
    }

    /**
     * 停用医疗服务：校验已发布状态后迁移至 RETIRED，并发布服务停用事件。
     *
     * <p>停用后服务不再对外提供，但可通过重新发布恢复。</p>
     *
     * @param cmd 停用医疗服务命令
     * @throws BusinessException 服务不存在或当前状态不允许停用
     */
    public void retireService(RetireHealthcareServiceCommand cmd) {
        List<RetireHealthcareServiceCommand.Item> items = cmd.resolveItems();
        // 批量加载待停用的医疗服务实体。
        Map<Long, HealthcareService> map = this.loadMap(CommandBatchUtil.extractIds(items, RetireHealthcareServiceCommand.Item::getServiceId));
        List<HealthcareService> toSave = new ArrayList<>();
        for (RetireHealthcareServiceCommand.Item item : items) {
            HealthcareService row = require(map, item.getServiceId());
            // 校验当前状态允许停用（仅已发布态），并执行状态迁移至 RETIRED。
            row.assertRetirable();
            row.transitToRetired();
            toSave.add(row);
        }
        // 先批量落库，确保停用状态持久化后再发布事件（B.14 硬门禁）。
        repository.saveAll(toSave);
        // 逐条发布医疗服务停用领域事件，通知下游更新服务可用性。
        for (RetireHealthcareServiceCommand.Item item : items) {
            domainEventPublisher.publish(new HealthcareServiceRetiredEvent(item.getServiceId(), item.getReason()));
        }
    }

    /**
     * 重新发布医疗服务：校验已停用状态后迁移至 ACTIVE，并发布服务重新激活事件。
     *
     * @param cmd 重新发布医疗服务命令
     * @throws BusinessException 服务不存在或当前状态不允许重新发布
     */
    public void reactivateService(ReactivateHealthcareServiceCommand cmd) {
        List<ReactivateHealthcareServiceCommand.Item> items = cmd.resolveItems();
        // 批量加载待重新发布的医疗服务实体。
        Map<Long, HealthcareService> map = this.loadMap(CommandBatchUtil.extractIds(items, ReactivateHealthcareServiceCommand.Item::getServiceId));
        List<HealthcareService> toSave = new ArrayList<>();
        for (ReactivateHealthcareServiceCommand.Item item : items) {
            HealthcareService row = require(map, item.getServiceId());
            // 校验当前状态允许重新发布（仅停用态），并执行状态迁移至 ACTIVE。
            row.assertReactivatable();
            row.transitToActive();
            toSave.add(row);
        }
        // 先批量落库，确保状态恢复持久化后再发布事件（B.14 硬门禁）。
        repository.saveAll(toSave);
        // 逐条发布医疗服务重新激活领域事件，通知下游恢复服务可用性。
        for (ReactivateHealthcareServiceCommand.Item item : items) {
            domainEventPublisher.publish(new HealthcareServiceReactivatedEvent(item.getServiceId()));
        }
    }

    /**
     * 废弃医疗服务：执行逻辑删除并发布服务废弃事件。
     *
     * <p>草稿或已停用状态的服务可以废弃；废弃后服务从系统中不可见，且不可恢复。</p>
     *
     * @param cmd 废弃医疗服务命令，需携带操作人 ID 用于软删审计
     * @throws BusinessException 服务不存在或当前状态不允许废弃
     */
    public void abolishService(AbolishHealthcareServiceCommand cmd) {
        List<AbolishHealthcareServiceCommand.Item> items = cmd.resolveItems();
        // 批量加载待废弃的医疗服务实体。
        Map<Long, HealthcareService> map = this.loadMap(CommandBatchUtil.extractIds(items, AbolishHealthcareServiceCommand.Item::getServiceId));
        List<HealthcareService> toSave = new ArrayList<>();
        for (AbolishHealthcareServiceCommand.Item item : items) {
            HealthcareService row = require(map, item.getServiceId());
            // 校验当前状态允许废弃（草稿或停用态），并执行逻辑删除标记。
            row.assertAbolishable();
            row.transitToDeleted(cmd.getOperatorId());
            toSave.add(row);
        }
        // 先批量落库，确保逻辑删除持久化后再发布废弃事件（B.14 硬门禁）。
        repository.saveAll(toSave);
        // 逐条发布医疗服务废弃领域事件，通知下游清理关联数据。
        for (AbolishHealthcareServiceCommand.Item item : items) {
            domainEventPublisher.publish(new HealthcareServiceAbolishedEvent(item.getServiceId(), item.getReason()));
        }
    }

    /**
     * 归一诊查费绑定 CSV：空白→null；否则 trim 各段后以逗号拼接。
     *
     * @param activityIdsCsv 原始绑定串
     * @return 归一后的 CSV 或 null
     */
    private String normalizeActivityIdsCsv(String activityIdsCsv) {
        if (activityIdsCsv == null || activityIdsCsv.isBlank()) {
            return null;
        }
        String[] tokens = activityIdsCsv.split(",");
        StringBuilder sb = new StringBuilder();
        for (String raw : tokens) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(raw.trim());
        }
        if (sb.length() == 0) {
            return null;
        }
        return sb.toString();
    }

    /**
     * 批量加载医疗服务，构建以主键为键的内存映射供后续 O(1) 查找。
     *
     * <p>过滤已逻辑删除的服务，保证候选操作集中只含有效记录。</p>
     *
     * @param ids 目标医疗服务主键列表
     * @return 以主键为键的有效医疗服务映射
     */
    private Map<Long, HealthcareService> loadMap(List<Long> ids) {
        List<HealthcareService> list = repository.findAllById(ids);
        Map<Long, HealthcareService> map = new HashMap<>();
        for (HealthcareService row : list) {
            // 过滤已逻辑删除的医疗服务，仅将有效记录纳入候选操作集。
            if (!Boolean.TRUE.equals(row.getIsDeleted())) {
                map.put(row.getId(), row);
            }
        }
        return map;
    }

    /**
     * 从内存映射中强制获取指定主键的医疗服务；若不存在则抛出业务异常，终止操作。
     *
     * @param map 以主键为键的医疗服务内存映射
     * @param id  目标医疗服务主键
     * @return 对应的医疗服务实体
     * @throws BusinessException 医疗服务不存在（HEALTHCARE_SERVICE_NOT_FOUND）
     */
    private HealthcareService require(Map<Long, HealthcareService> map, Long id) {
        HealthcareService row = map.get(id);
        // 指定 ID 的医疗服务不在有效候选集中，说明不存在或已被逻辑删除，终止操作。
        if (row == null) {
            throw new BusinessException(ErrorCodeConstants.HEALTHCARE_SERVICE_NOT_FOUND, "医疗服务不存在");
        }
        return row;
    }
}
