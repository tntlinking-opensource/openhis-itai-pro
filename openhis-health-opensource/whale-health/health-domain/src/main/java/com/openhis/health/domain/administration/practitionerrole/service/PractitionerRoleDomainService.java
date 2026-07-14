/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.practitionerrole.service;


import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.administration.practitionerrole.command.*;
import com.openhis.health.domain.administration.practitionerrole.entity.PractitionerRoleScheduleItem;
import com.openhis.health.domain.administration.practitionerrole.entity.PractitionerRoleServiceItem;
import com.openhis.health.domain.administration.practitionerrole.entity.PractitionerRole;
import com.openhis.health.domain.administration.practitionerrole.repository.PractitionerRoleRepository;
import com.openhis.health.domain.administration.practitionerrole.repository.PractitionerRoleScheduleItemRepository;
import com.openhis.health.domain.administration.practitionerrole.repository.PractitionerRoleServiceItemRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.administration.practitionerrole.*;
import com.openhis.health.domain.share.utils.CommandBatchUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import lombok.RequiredArgsConstructor;

/**
 * 执业角色领域服务，承载执业角色（PractitionerRole）的写侧编排能力。
 *
 * <p>管理岗位分配、编辑、激活/停用、注销、服务范围更新和排班更新全流程。
 * 所有写操作均遵循「先落库后发事件」时序（通用规范 B.14 硬门禁），确保事件与数据一致性。</p>
 *
 * <p>对应 DM-ADM-006 §6.1 写侧方法集。</p>
 */
@Service
@RequiredArgsConstructor
public class PractitionerRoleDomainService {
    private final PractitionerRoleRepository repository;
    private final PractitionerRoleServiceItemRepository serviceItemRepository;
    private final PractitionerRoleScheduleItemRepository scheduleItemRepository;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 分配执业角色：校验医务人员存在性与岗位唯一性后，批量创建执业角色记录并发布领域事件。
     *
     * @param cmd 分配执业角色命令，包含一条或多条分配项
     * @return 首条新建执业角色记录的主键
     * @throws BusinessException 医务人员不存在（PRACTITIONER_ROLE_RULE_CONFLICT）或岗位重复（PRACTITIONER_ROLE_CONFLICT）
     */
    public Long assignRole(AssignPractitionerRoleCommand cmd) {
        List<AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand> items = cmd.resolveItems();
        // 逐条进行前置校验：医务人员存在性与岗位唯一性必须全部通过才允许创建。
        List<PractitionerRole> toSave = new ArrayList<>();
        Set<String> batchBusinessKeys = new HashSet<>();
        for (AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand item : items) {
            // 批内唯一性校验：同一批次中不允许出现相同业务唯一键，避免落库阶段才因冲突失败。
            String batchKey = buildUniqueBusinessKey(item.getPractitionerId(), item.getRoleId(), item.getOrgId(), item.getLocationId());
            if (!batchBusinessKeys.add(batchKey)) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_CONFLICT, "岗位分配重复");
            }
            assertAssignItemBusinessRules(item);
            PractitionerRole role = resolveAssignableRoleRow(item);
            toSave.add(role);
        }
        // 批量落库，确保所有岗位数据持久化后再发布领域事件（B.14 硬门禁）。
        List<PractitionerRole> saved = repository.saveAll(toSave);

        // 组装 saveAll 结果映射，避免依赖返回顺序。
        Map<String, PractitionerRole> roleByBusinessKey = new HashMap<>();
        for (PractitionerRole role : saved) {
            String key = buildUniqueBusinessKey(role.getPractitionerId(), role.getRoleId(), role.getOrgId(), role.getLocationId());
            roleByBusinessKey.put(key, role);
        }

        // 写入子表：岗位服务项与岗位排班项（全量对齐；复用行先删后插）。
        persistAssignItemChildren(items, roleByBusinessKey);

        // 子表与主表均已落库后再逐条发布岗位分配领域事件（B.14 硬门禁）。
        for (PractitionerRole role : saved) {
            domainEventPublisher.publish(new PractitionerRoleCreatedEvent(role.getId(), role.getPractitionerId(), role.getOrgId(), role.getRoleId()));
        }
        return saved.get(0).getId();
    }

    /**
     * 编辑执业角色信息：更新科室、地点、名称，并发布角色信息修改事件。
     *
     * @param cmd 编辑执业角色命令，包含一条或多条编辑项
     * @throws BusinessException 角色不存在或当前状态不允许编辑
     */
    public void editRole(EditPractitionerRoleCommand cmd) {
        List<EditPractitionerRoleCommand.EditPractitionerRoleItemCommand> items = cmd.resolveItems();
        // 批量加载待编辑的执业角色，确认记录存在且未被逻辑删除。
        Map<Long, PractitionerRole> map = loadMap(CommandBatchUtil.extractIds(items, EditPractitionerRoleCommand.EditPractitionerRoleItemCommand::getAssignmentId));
        List<PractitionerRole> toSave = new ArrayList<>();
        Map<Long, List<String>> changedFieldsByAssignmentId = new HashMap<>();
        for (EditPractitionerRoleCommand.EditPractitionerRoleItemCommand item : items) {
            PractitionerRole role = require(map, item.getAssignmentId());
            // 校验当前角色状态允许编辑（委托聚合根方法，避免服务层直接比较枚举值）。
            role.assertEditable();
            Long locationId = parseSingleEditLocationId(item.getLocationId());
            // 校验组织/位置至少一项非空（BR-PRL-007）。
            if (item.getOrgId() == null && locationId == null) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_RULE_CONFLICT, "科室与位置不可同时为空");
            }
            // 唯一性校验：编辑后仍需满足人员+角色+科室+位置唯一（BR-PRL-001，不含已软删行）。
            if (repository.existsByPractitionerIdAndRoleIdAndOrgIdAndLocationIdAndIdNotAndIsDeletedFalse(
                role.getPractitionerId(), role.getRoleId(), item.getOrgId(), locationId, role.getId())) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_CONFLICT, "岗位分配重复");
            }

            List<String> changedFields = new ArrayList<>();
            if (!Objects.equals(role.getName(), item.getName())) {
                changedFields.add(FieldNameConstants.NAME);
            }
            if (!Objects.equals(role.getOrgId(), item.getOrgId())) {
                changedFields.add(FieldNameConstants.ORG_ID);
            }
            if (!Objects.equals(role.getLocationId(), locationId)) {
                changedFields.add(FieldNameConstants.LOCATION_ID);
            }
            if (!Objects.equals(role.getSpecialtyCode(), item.getSpecialtyCode())) {
                changedFields.add(FieldNameConstants.SPECIALTY_CODE);
            }
            if (!Objects.equals(role.getTypeCode(), item.getTypeCode())) {
                changedFields.add(FieldNameConstants.TYPE_CODE);
            }

            role.setOrgId(item.getOrgId());
            role.setLocationId(locationId);
            role.setName(item.getName());
            role.setSpecialtyCode(item.getSpecialtyCode());
            role.setTypeCode(item.getTypeCode());
            toSave.add(role);
            changedFieldsByAssignmentId.put(item.getAssignmentId(), changedFields);
        }
        // 先批量落库，确保字段变更持久化后再发布事件（B.14 硬门禁）。
        repository.saveAll(toSave);
        // 逐条发布岗位信息修改领域事件，通知下游同步变更字段。
        for (EditPractitionerRoleCommand.EditPractitionerRoleItemCommand item : items) {
            domainEventPublisher.publish(new PractitionerRoleEditedEvent(item.getAssignmentId(), changedFieldsByAssignmentId.get(item.getAssignmentId())));
        }
    }

    /**
     * 激活执业角色：校验状态后迁移至 ACTIVE，并发布角色激活事件。
     *
     * @param cmd 激活执业角色命令
     * @throws BusinessException 角色不存在或当前状态不允许激活
     */
    public void activateRole(ActivatePractitionerRoleCommand cmd) {
        List<ActivatePractitionerRoleCommand.ActivatePractitionerRoleItemCommand> items = cmd.resolveItems();
        // 批量加载待激活的执业角色实体。
        Map<Long, PractitionerRole> map = loadMap(CommandBatchUtil.extractIds(items, ActivatePractitionerRoleCommand.ActivatePractitionerRoleItemCommand::getAssignmentId));
        List<PractitionerRole> toSave = new ArrayList<>();
        for (ActivatePractitionerRoleCommand.ActivatePractitionerRoleItemCommand item : items) {
            PractitionerRole role = require(map, item.getAssignmentId());
            // 校验当前状态允许激活，并执行状态迁移至 ACTIVE。
            role.assertActivatable();
            role.transitToActive();
            toSave.add(role);
        }
        // 先批量落库，确保状态变更持久化后再发布激活事件（B.14 硬门禁）。
        repository.saveAll(toSave);
        // 逐条发布岗位激活领域事件，通知下游读模型更新角色状态。
        for (ActivatePractitionerRoleCommand.ActivatePractitionerRoleItemCommand item : items) {
            PractitionerRole role = require(map, item.getAssignmentId());
            domainEventPublisher.publish(new PractitionerRoleActivatedEvent(role.getId(), role.getPractitionerId(), role.getOrgId()));
        }
    }

    /**
     * 停用执业角色：校验状态后迁移至 INACTIVE，并发布角色停用事件。
     *
     * @param cmd 停用执业角色命令
     * @throws BusinessException 角色不存在或当前状态不允许停用
     */
    public void deactivateRole(DeactivatePractitionerRoleCommand cmd) {
        List<DeactivatePractitionerRoleCommand.DeactivatePractitionerRoleItemCommand> items = cmd.resolveItems();
        // 批量加载待停用的执业角色实体。
        Map<Long, PractitionerRole> map = loadMap(CommandBatchUtil.extractIds(items, DeactivatePractitionerRoleCommand.DeactivatePractitionerRoleItemCommand::getAssignmentId));
        List<PractitionerRole> toSave = new ArrayList<>();
        for (DeactivatePractitionerRoleCommand.DeactivatePractitionerRoleItemCommand item : items) {
            PractitionerRole role = require(map, item.getAssignmentId());
            // 校验当前状态允许停用，并执行状态迁移至 INACTIVE。
            role.assertDeactivatable();
            role.transitToInactive();
            toSave.add(role);
        }
        // 先批量落库，确保状态变更持久化后再发布停用事件（B.14 硬门禁）。
        repository.saveAll(toSave);
        // 逐条发布岗位停用领域事件，通知下游同步角色不可用状态。
        for (DeactivatePractitionerRoleCommand.DeactivatePractitionerRoleItemCommand item : items) {
            domainEventPublisher.publish(new PractitionerRoleDeactivatedEvent(item.getAssignmentId(), item.getReason()));
        }
    }

    /**
     * 注销执业角色：执行逻辑删除并发布角色废弃事件。
     *
     * @param cmd 注销执业角色命令，需携带操作人 ID 用于软删审计
     * @throws BusinessException 角色不存在或当前状态不允许注销
     */
    public void retireRole(RetirePractitionerRoleCommand cmd) {
        List<RetirePractitionerRoleCommand.RetirePractitionerRoleItemCommand> items = cmd.resolveItems();
        // 批量加载待注销的执业角色实体。
        Map<Long, PractitionerRole> map = loadMap(CommandBatchUtil.extractIds(items, RetirePractitionerRoleCommand.RetirePractitionerRoleItemCommand::getAssignmentId));
        List<PractitionerRole> toSave = new ArrayList<>();
        for (RetirePractitionerRoleCommand.RetirePractitionerRoleItemCommand item : items) {
            PractitionerRole role = require(map, item.getAssignmentId());
            // 校验当前状态允许注销，并执行逻辑删除（标记 isDeleted + 记录操作人）。
            role.assertRetirable();
            role.transitToDeleted(cmd.getOperatorId());
            toSave.add(role);
        }
        // 先批量落库，确保逻辑删除持久化后再发布废弃事件（B.14 硬门禁）。
        repository.saveAll(toSave);
        // 逐条发布岗位废弃领域事件，通知下游清理角色关联数据。
        for (RetirePractitionerRoleCommand.RetirePractitionerRoleItemCommand item : items) {
            PractitionerRole role = require(map, item.getAssignmentId());
            domainEventPublisher.publish(new PractitionerRoleAbolishedEvent(role.getId(), role.getPractitionerId()));
        }
    }

    /**
     * 更新执业角色的科室服务范围（serviceIds），并发布服务更新事件。
     *
     * @param cmd 更新服务命令，包含新的科室服务 ID 列表
     * @throws BusinessException 角色不存在或当前状态不允许更新服务
     */
    public void updateServices(UpdatePractitionerRoleServicesCommand cmd) {
        List<UpdatePractitionerRoleServicesCommand.UpdatePractitionerRoleServicesItemCommand> items = cmd.resolveItems();
        // 批量加载待更新的执业角色实体，确认记录存在。
        Map<Long, PractitionerRole> map = loadMap(CommandBatchUtil.extractIds(items, UpdatePractitionerRoleServicesCommand.UpdatePractitionerRoleServicesItemCommand::getAssignmentId));
        List<Long> assignmentIds = new ArrayList<>();
        List<PractitionerRoleServiceItem> toSaveServiceItems = new ArrayList<>();
        for (UpdatePractitionerRoleServicesCommand.UpdatePractitionerRoleServicesItemCommand item : items) {
            PractitionerRole role = require(map, item.getAssignmentId());
            // 校验当前状态允许更新科室服务范围。
            role.assertServiceUpdatable();
            assignmentIds.add(item.getAssignmentId());

            // 服务项去重校验（BR-PRL-010）与引用校验（BR-PRL-002）。
            if (item.getServiceIds() != null && !item.getServiceIds().isEmpty()) {
                Set<Long> set = new LinkedHashSet<>(item.getServiceIds());
                if (set.size() != item.getServiceIds().size()) {
                    throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_RULE_CONFLICT, "同一岗位下就诊类型不允许重复");
                }
                for (Long serviceId : set) {
                    if (serviceId == null) {
                        throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_RULE_CONFLICT, "就诊类型不存在");
                    }
                    PractitionerRoleServiceItem row = new PractitionerRoleServiceItem();
                    row.setAssignmentId(item.getAssignmentId());
                    row.setHealthcareServiceId(serviceId);
                    toSaveServiceItems.add(row);
                }
            }
        }

        // 先按 assignmentId 作用域全量对齐子表：删除旧行，再批量写入新行（B.14：先落库后发事件）。
        if (!assignmentIds.isEmpty()) {
            serviceItemRepository.deleteAllByAssignmentIdIn(assignmentIds);
        }
        if (!toSaveServiceItems.isEmpty()) {
            serviceItemRepository.saveAll(toSaveServiceItems);
        }

        // 逐条发布科室服务更新领域事件，通知下游同步服务列表变更。
        for (UpdatePractitionerRoleServicesCommand.UpdatePractitionerRoleServicesItemCommand item : items) {
            domainEventPublisher.publish(new PractitionerRoleServiceUpdatedEvent(item.getAssignmentId(), item.getServiceIds()));
        }
    }

    /**
     * 更新执业角色的排班信息（availabilityJson），并发布排班更新事件。
     *
     * @param cmd 更新排班命令，包含新的排班 JSON 字符串
     * @throws BusinessException 角色不存在或当前状态不允许更新排班
     */
    public void updateSchedule(UpdatePractitionerRoleScheduleCommand cmd) {
        List<UpdatePractitionerRoleScheduleCommand.UpdatePractitionerRoleScheduleItemCommand> items = cmd.resolveItems();
        // 批量加载待更新排班的执业角色实体。
        Map<Long, PractitionerRole> map = loadMap(CommandBatchUtil.extractIds(items, UpdatePractitionerRoleScheduleCommand.UpdatePractitionerRoleScheduleItemCommand::getAssignmentId));
        List<Long> assignmentIds = new ArrayList<>();
        List<PractitionerRoleScheduleItem> toSaveScheduleItems = new ArrayList<>();
        for (UpdatePractitionerRoleScheduleCommand.UpdatePractitionerRoleScheduleItemCommand item : items) {
            PractitionerRole role = require(map, item.getAssignmentId());
            // 校验当前状态允许更新排班信息。
            role.assertScheduleUpdatable();
            assignmentIds.add(item.getAssignmentId());
            // 排班 JSON 校验：每个排班项必须为合法 JSON（BR-PRL-003 的最小落点）。
            if (item.getScheduleItems() != null) {
                for (AvailabilityScheduleCommand row : item.getScheduleItems()) {
                    if (row == null || row.getAvailabilityJson() == null || row.getAvailabilityJson().isBlank()) {
                        throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "排班数据不能为空");
                    }
                    assertValidJson(row.getAvailabilityJson());
                    PractitionerRoleScheduleItem entity = new PractitionerRoleScheduleItem();
                    entity.setAssignmentId(item.getAssignmentId());
                    entity.setAvailabilityJson(row.getAvailabilityJson());
                    toSaveScheduleItems.add(entity);
                }
            }
        }
        // 先按 assignmentId 作用域全量对齐子表：删除旧行，再批量写入新行（B.14：先落库后发事件）。
        if (!assignmentIds.isEmpty()) {
            scheduleItemRepository.deleteAllByAssignmentIdIn(assignmentIds);
        }
        if (!toSaveScheduleItems.isEmpty()) {
            scheduleItemRepository.saveAll(toSaveScheduleItems);
        }
        // 逐条发布排班更新领域事件，通知下游同步排班信息。
        for (UpdatePractitionerRoleScheduleCommand.UpdatePractitionerRoleScheduleItemCommand item : items) {
            // 以新排班项的 JSON 列表作为“可用时间摘要”最小实现，保证下游可据此判断变更内容。
            List<String> jsons = new ArrayList<>();
            if (item.getScheduleItems() != null) {
                for (AvailabilityScheduleCommand row : item.getScheduleItems()) {
                    if (row != null && row.getAvailabilityJson() != null) {
                        jsons.add(row.getAvailabilityJson());
                    }
                }
            }
            String summary;
            try {
                summary = new ObjectMapper().writeValueAsString(jsons);
            } catch (Exception ex) {
                summary = jsons.toString();
            }
            domainEventPublisher.publish(new PractitionerRoleScheduleUpdatedEvent(item.getAssignmentId(), summary));
        }
    }

    /**
     * 按角色类型批量替换执业角色：先逻辑删除同类型历史岗位，再分配新岗位，最后发布替换事件。
     *
     * <p>适用于医务人员调岗场景：保证同类型岗位在同一时刻只有一份有效记录。</p>
     *
     * @param cmd 按类型替换执业角色命令
     * @return 被替换（逻辑删除）的历史岗位主键列表
     * @throws BusinessException 医务人员或角色校验失败（由内部 assignRole 抛出）
     */
    public List<Long> replaceRolesByType(ReplacePractitionerRolesByTypeCommand cmd) {
        // 归一化替换入参：替换维度由顶层 practitionerId + roleId 决定，items 中若携带同字段必须一致。
        if (cmd.getItems() != null) {
            for (AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand item : cmd.getItems()) {
                if (item == null) {
                    continue;
                }
                if (item.getPractitionerId() == null) {
                    item.setPractitionerId(cmd.getPractitionerId());
                } else if (!item.getPractitionerId().equals(cmd.getPractitionerId())) {
                    throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "批量替换的医务人员标识不一致");
                }
                if (item.getRoleId() == null) {
                    item.setRoleId(cmd.getRoleId());
                } else if (!item.getRoleId().equals(cmd.getRoleId())) {
                    throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "批量替换的平台角色不一致");
                }
            }
        }
        List<AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand> items;
        if (cmd.getItems() == null) {
            items = List.of();
        } else {
            items = cmd.getItems();
        }
        // Pre：加载同角色下全部历史行（含软删），供「保留/复用/废弃」对齐。
        List<PractitionerRole> allExisting =
            repository.findAllByPractitionerIdAndRoleId(cmd.getPractitionerId(), cmd.getRoleId());
        Map<String, PractitionerRole> existingByKey = indexExistingByBusinessKey(allExisting);
        Set<String> desiredKeys = new LinkedHashSet<>();
        Set<String> batchBusinessKeys = new HashSet<>();
        List<PractitionerRole> toSave = new ArrayList<>();
        for (AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand item : items) {
            if (item == null) {
                continue;
            }
            String batchKey = buildUniqueBusinessKey(item.getPractitionerId(), item.getRoleId(), item.getOrgId(), item.getLocationId());
            if (!batchBusinessKeys.add(batchKey)) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_CONFLICT, "岗位分配重复");
            }
            assertAssignItemBusinessRules(item);
            desiredKeys.add(batchKey);
            PractitionerRole existing = existingByKey.get(batchKey);
            if (existing != null) {
                // 业务分支：目标键已存在时复用历史行（含软删），避免「先删后插」触发唯一性冲突。
                if (Boolean.TRUE.equals(existing.getIsDeleted())) {
                    existing.reactivateFromSync();
                }
                applyAssignItemFields(existing, item);
                toSave.add(existing);
            } else {
                PractitionerRole role = newAssignableRoleFromItem(item);
                toSave.add(role);
            }
        }
        for (PractitionerRole row : allExisting) {
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            String key = buildUniqueBusinessKey(row.getPractitionerId(), row.getRoleId(), row.getOrgId(), row.getLocationId());
            if (!desiredKeys.contains(key)) {
                // 业务分支：请求未携带的历史有效行一律软删，实现全量对齐。
                row.transitToDeleted(cmd.getOperatorId());
                toSave.add(row);
            }
        }
        List<PractitionerRole> saved;
        if (toSave.isEmpty()) {
            saved = List.of();
        } else {
            saved = repository.saveAll(toSave);
        }
        Map<String, PractitionerRole> roleByBusinessKey = new HashMap<>();
        for (PractitionerRole role : saved) {
            if (Boolean.TRUE.equals(role.getIsDeleted())) {
                continue;
            }
            roleByBusinessKey.put(
                buildUniqueBusinessKey(role.getPractitionerId(), role.getRoleId(), role.getOrgId(), role.getLocationId()),
                role);
        }
        persistAssignItemChildren(items, roleByBusinessKey);
        List<Long> newAssignmentIds = new ArrayList<>();
        for (String key : desiredKeys) {
            PractitionerRole role = roleByBusinessKey.get(key);
            if (role != null && role.getId() != null) {
                newAssignmentIds.add(role.getId());
            }
        }
        // 发布批量替换完成的领域事件，携带替换后岗位 ID 列表供下游消费（DM §5.1：替换后岗位ID列表）。
        domainEventPublisher.publish(new PractitionerRoleBatchReplacedEvent(cmd.getPractitionerId(), cmd.getRoleId(), newAssignmentIds));
        return newAssignmentIds;
    }

    /**
     * 批量加载执业角色，构建以主键为键的内存映射供后续 O(1) 查找。
     *
     * <p>过滤已逻辑删除的角色，保证候选操作集中只含有效记录。</p>
     *
     * @param ids 目标执业角色主键列表
     * @return 以主键为键的有效执业角色映射
     */
    private Map<Long, PractitionerRole> loadMap(List<Long> ids) {
        List<PractitionerRole> list = repository.findAllById(ids);
        Map<Long, PractitionerRole> map = new HashMap<>();
        for (PractitionerRole item : list) {
            // 过滤已逻辑删除的执业角色，仅将有效记录纳入候选操作集；已删除记录不参与后续业务操作。
            if (!Boolean.TRUE.equals(item.getIsDeleted())) {
                map.put(item.getId(), item);
            }
        }
        return map;
    }

    /**
     * 从内存映射中强制获取指定主键的执业角色；若不存在则抛出业务异常，终止操作。
     *
     * @param map 以主键为键的执业角色内存映射
     * @param id  目标执业角色主键
     * @return 对应的执业角色实体
     * @throws BusinessException 执业角色不存在（PRACTITIONER_ROLE_NOT_FOUND）
     */
    private PractitionerRole require(Map<Long, PractitionerRole> map, Long id) {
        PractitionerRole role = map.get(id);
        // 指定 ID 的执业角色不在有效候选集中，说明记录不存在或已被逻辑删除，终止操作。
        if (role == null) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_NOT_FOUND, "岗位不存在");
        }
        return role;
    }

    /**
     * 构建岗位分配唯一业务键：practitionerId + roleId + orgId + locationId。
     *
     * <p>分隔符使用不可见控制字符，避免业务值中常见字符（如 {@code :}、{@code |}）导致拼接碰撞。</p>
     *
     * @param practitionerId 医务人员标识
     * @param roleId         平台角色主键
     * @param orgId          科室标识（可空）
     * @param locationId     位置标识（可空）
     * @return 唯一业务键
     */
    private String buildUniqueBusinessKey(Long practitionerId, Long roleId, Long orgId, Long locationId) {
        String sep = "\u0001";
        return String.valueOf(practitionerId) + sep + String.valueOf(roleId) + sep + String.valueOf(orgId) + sep + String.valueOf(locationId);
    }

    /**
     * 普通岗位编辑：locationId 须为单个 Long；含逗号或格式非法时抛异常。
     *
     * @param locationId 位置标识字符串
     * @return 解析后的库位 id；空串 null
     * @throws BusinessException 多 id 或格式无效
     */
    private Long parseSingleEditLocationId(String locationId) {
        if (locationId == null || locationId.isBlank()) {
            return null;
        }
        String trimmed = locationId.trim();
        if (trimmed.contains(",")) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "普通岗位位置只能为单个标识");
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "位置标识格式无效");
        }
    }

    /**
     * 分配项业务规则校验：号源去重与排班 JSON 合法性（BR-PRL-003 / BR-PRL-010）。
     *
     * @param item 单条分配项
     */
    private void assertAssignItemBusinessRules(AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand item) {
        if (item.getServiceIds() != null) {
            Set<Long> set = new HashSet<>(item.getServiceIds());
            if (set.size() != item.getServiceIds().size()) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_RULE_CONFLICT, "同一岗位下就诊类型不允许重复");
            }
        }
        if (item.getScheduleItems() != null) {
            for (AvailabilityScheduleCommand row : item.getScheduleItems()) {
                if (row == null || row.getAvailabilityJson() == null || row.getAvailabilityJson().isBlank()) {
                    throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "排班数据不能为空");
                }
                assertValidJson(row.getAvailabilityJson());
            }
        }
    }

    /**
     * 解析待落库岗位行：有效行冲突抛错；软删行复用；否则新建。
     *
     * @param item 分配项
     * @return 可持久化的岗位实体
     */
    private PractitionerRole resolveAssignableRoleRow(AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand item) {
        List<PractitionerRole> history = repository.findAllByPractitionerIdAndRoleIdAndOrgIdAndLocationId(
            item.getPractitionerId(), item.getRoleId(), item.getOrgId(), item.getLocationId());
        PractitionerRole reusable = pickReusableRoleRow(history);
        if (reusable != null) {
            if (!Boolean.TRUE.equals(reusable.getIsDeleted())) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_CONFLICT, "岗位分配重复");
            }
            reusable.reactivateFromSync();
            applyAssignItemFields(reusable, item);
            return reusable;
        }
        return newAssignableRoleFromItem(item);
    }

    /**
     * 由分配项组装新岗位实体（不含子表）。
     *
     * @param item 分配项
     * @return 新岗位实体
     */
    private PractitionerRole newAssignableRoleFromItem(AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand item) {
        PractitionerRole role = new PractitionerRole();
        applyAssignItemFields(role, item);
        role.assertAssignable();
        role.initAsActive();
        return role;
    }

    /**
     * 将分配项主表字段写入岗位实体。
     *
     * @param role 目标实体
     * @param item 分配项
     */
    private void applyAssignItemFields(
        PractitionerRole role,
        AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand item) {
        role.setPractitionerId(item.getPractitionerId());
        role.setOrgId(item.getOrgId());
        role.setLocationId(item.getLocationId());
        role.setRoleId(item.getRoleId());
        role.setName(item.getName());
        role.setSpecialtyCode(item.getSpecialtyCode());
        role.setTypeCode(item.getTypeCode());
    }

    /**
     * 按业务键索引历史岗位行；同键多条时优先保留未软删行。
     *
     * @param rows 历史岗位列表
     * @return 业务键 → 岗位实体
     */
    private Map<String, PractitionerRole> indexExistingByBusinessKey(List<PractitionerRole> rows) {
        Map<String, PractitionerRole> map = new HashMap<>();
        for (PractitionerRole row : rows) {
            if (row == null) {
                continue;
            }
            String key = buildUniqueBusinessKey(row.getPractitionerId(), row.getRoleId(), row.getOrgId(), row.getLocationId());
            PractitionerRole current = map.get(key);
            if (current == null) {
                map.put(key, row);
                continue;
            }
            if (Boolean.TRUE.equals(current.getIsDeleted()) && !Boolean.TRUE.equals(row.getIsDeleted())) {
                map.put(key, row);
            }
        }
        return map;
    }

    /**
     * 从历史同键行中挑选可复用记录：优先有效行，否则取最近软删行。
     *
     * @param history 同业务键历史行
     * @return 可复用行；无历史时为 {@code null}
     */
    private PractitionerRole pickReusableRoleRow(List<PractitionerRole> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        PractitionerRole active = null;
        PractitionerRole latestDeleted = null;
        for (PractitionerRole row : history) {
            if (row == null) {
                continue;
            }
            if (!Boolean.TRUE.equals(row.getIsDeleted())) {
                active = row;
                break;
            }
            latestDeleted = row;
        }
        if (active != null) {
            return active;
        }
        return latestDeleted;
    }

    /**
     * 写入岗位服务项与排班项子表（全量对齐插入；空集合则不插入）。
     *
     * @param items             分配项列表
     * @param roleByBusinessKey 主表落库后的业务键映射
     */
    private void persistAssignItemChildren(
        List<AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand> items,
        Map<String, PractitionerRole> roleByBusinessKey) {
        if (items == null || items.isEmpty() || roleByBusinessKey.isEmpty()) {
            return;
        }
        List<Long> serviceAssignmentIds = new ArrayList<>();
        List<Long> scheduleAssignmentIds = new ArrayList<>();
        List<PractitionerRoleServiceItem> serviceItemsToSave = new ArrayList<>();
        List<PractitionerRoleScheduleItem> scheduleItemsToSave = new ArrayList<>();
        for (AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand item : items) {
            if (item == null) {
                continue;
            }
            String key = buildUniqueBusinessKey(item.getPractitionerId(), item.getRoleId(), item.getOrgId(), item.getLocationId());
            PractitionerRole role = roleByBusinessKey.get(key);
            if (role == null) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_CONFLICT, "岗位落库结果回绑失败");
            }
            if (item.getServiceIds() != null && !item.getServiceIds().isEmpty()) {
                serviceAssignmentIds.add(role.getId());
                Set<Long> set = new LinkedHashSet<>(item.getServiceIds());
                for (Long serviceId : set) {
                    PractitionerRoleServiceItem row = new PractitionerRoleServiceItem();
                    row.setAssignmentId(role.getId());
                    row.setHealthcareServiceId(serviceId);
                    serviceItemsToSave.add(row);
                }
            }
            if (item.getScheduleItems() != null && !item.getScheduleItems().isEmpty()) {
                scheduleAssignmentIds.add(role.getId());
                for (AvailabilityScheduleCommand schedule : item.getScheduleItems()) {
                    if (schedule == null) {
                        continue;
                    }
                    if (schedule.getAvailabilityJson() == null || schedule.getAvailabilityJson().isBlank()) {
                        continue;
                    }
                    PractitionerRoleScheduleItem row = new PractitionerRoleScheduleItem();
                    row.setAssignmentId(role.getId());
                    row.setAvailabilityJson(schedule.getAvailabilityJson());
                    scheduleItemsToSave.add(row);
                }
            }
        }
        if (!serviceAssignmentIds.isEmpty()) {
            serviceItemRepository.deleteAllByAssignmentIdIn(serviceAssignmentIds);
        }
        if (!scheduleAssignmentIds.isEmpty()) {
            scheduleItemRepository.deleteAllByAssignmentIdIn(scheduleAssignmentIds);
        }
        if (!serviceItemsToSave.isEmpty()) {
            serviceItemRepository.saveAll(serviceItemsToSave);
        }
        if (!scheduleItemsToSave.isEmpty()) {
            scheduleItemRepository.saveAll(scheduleItemsToSave);
        }
    }

    /**
     * 断言字符串为合法 JSON。
     *
     * <p>本方法提供 BR-PRL-003 的最小落点：保证排班快照至少是可解析 JSON，
     * 复杂 schema 约束由上游应用层或后续专用校验组件补齐。</p>
     *
     * @param json JSON 字符串
     */
    private void assertValidJson(String json) {
        try {
            new ObjectMapper().readTree(json);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_RULE_CONFLICT, "排班数据不合法");
        }
    }
}
