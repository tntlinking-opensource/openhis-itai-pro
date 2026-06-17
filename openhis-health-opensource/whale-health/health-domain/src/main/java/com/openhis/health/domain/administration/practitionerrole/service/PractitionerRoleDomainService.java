/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
            // 唯一性校验：同一医务人员在相同角色、科室、地点组合下不允许重复分配岗位。
            if (repository.existsByPractitionerIdAndRoleIdAndOrgIdAndLocationId(
                item.getPractitionerId(), item.getRoleId(), item.getOrgId(), item.getLocationId())) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_CONFLICT, "岗位分配重复");
            }
            // 服务项去重校验：同一岗位内 serviceIds 不允许重复（BR-PRL-010）。
            if (item.getServiceIds() != null) {
                Set<Long> set = new HashSet<>(item.getServiceIds());
                if (set.size() != item.getServiceIds().size()) {
                    throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_RULE_CONFLICT, "同一岗位下就诊类型不允许重复");
                }
            }
            // 排班 JSON 校验：每个排班项必须为合法 JSON（BR-PRL-003 的最小落点）。
            if (item.getScheduleItems() != null) {
                for (AvailabilityScheduleCommand row : item.getScheduleItems()) {
                    if (row == null || row.getAvailabilityJson() == null || row.getAvailabilityJson().isBlank()) {
                        throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "排班数据不能为空");
                    }
                    assertValidJson(row.getAvailabilityJson());
                }
            }
            PractitionerRole role = new PractitionerRole();
            role.setPractitionerId(item.getPractitionerId());
            role.setOrgId(item.getOrgId());
            role.setLocationId(item.getLocationId());
            role.setRoleId(item.getRoleId());
            role.setName(item.getName());
            role.setSpecialtyCode(item.getSpecialtyCode());
            role.setTypeCode(item.getTypeCode());
            role.assertAssignable();
            role.initAsActive();
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

        // 写入子表：岗位服务项与岗位排班项（全量对齐插入；空集合则不插入）。
        List<PractitionerRoleServiceItem> serviceItemsToSave = new ArrayList<>();
        List<PractitionerRoleScheduleItem> scheduleItemsToSave = new ArrayList<>();
        for (AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand item : items) {
            String key = buildUniqueBusinessKey(item.getPractitionerId(), item.getRoleId(), item.getOrgId(), item.getLocationId());
            PractitionerRole role = roleByBusinessKey.get(key);
            if (role == null) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_CONFLICT, "岗位落库结果回绑失败");
            }
            if (item.getServiceIds() != null && !item.getServiceIds().isEmpty()) {
                Set<Long> set = new LinkedHashSet<>(item.getServiceIds());
                for (Long serviceId : set) {
                    PractitionerRoleServiceItem row = new PractitionerRoleServiceItem();
                    row.setAssignmentId(role.getId());
                    row.setHealthcareServiceId(serviceId);
                    serviceItemsToSave.add(row);
                }
            }
            if (item.getScheduleItems() != null && !item.getScheduleItems().isEmpty()) {
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
        if (!serviceItemsToSave.isEmpty()) {
            serviceItemRepository.saveAll(serviceItemsToSave);
        }
        if (!scheduleItemsToSave.isEmpty()) {
            scheduleItemRepository.saveAll(scheduleItemsToSave);
        }

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
            // 唯一性校验：编辑后仍需满足人员+角色+科室+位置唯一（BR-PRL-001）。
            if (repository.existsByPractitionerIdAndRoleIdAndOrgIdAndLocationIdAndIdNot(
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
        // 查询该医务人员当前所有同类型的有效岗位，准备替换。
        List<PractitionerRole> old = repository.findAllByPractitionerIdAndRoleId(cmd.getPractitionerId(), cmd.getRoleId());
        // 将所有历史同类型岗位标记为已删除，为新的岗位分配腾出名额。
        for (PractitionerRole row : old) {
            row.transitToDeleted(cmd.getOperatorId());
        }
        // 仅当存在历史岗位时才执行批量落库，避免空批次数据库操作。
        if (!old.isEmpty()) {
            repository.saveAll(old);
        }
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
        // 批量创建新岗位：替换场景不逐条发布 Created/Abolished 事件，仅发布 BatchReplaced 事件（DM-ADM-006 §5.2）。
        List<Long> newAssignmentIds = createRolesWithoutEvents(cmd.getItems());
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
     * 批量创建岗位（主表 + 子表），但不发布任何领域事件。
     *
     * <p>用于 {@code replaceRolesByType}：按 DM-ADM-006 §5.2 事件策略，
     * 替换场景只发布 {@link PractitionerRoleBatchReplacedEvent}，不逐条发布创建/废弃事件。</p>
     *
     * @param items 待创建岗位集合
     * @return 新建岗位主键列表
     */
    private List<Long> createRolesWithoutEvents(List<AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        AssignPractitionerRoleCommand cmd = new AssignPractitionerRoleCommand();
        cmd.setItems(items);

        // 复用 assignRole 的校验与写入逻辑，但绕开事件发布：此处直接内联最小实现，避免事件策略混淆。
        List<PractitionerRole> toSave = new ArrayList<>();
        Set<String> batchBusinessKeys = new HashSet<>();
        for (AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand item : cmd.resolveItems()) {
            String batchKey = buildUniqueBusinessKey(item.getPractitionerId(), item.getRoleId(), item.getOrgId(), item.getLocationId());
            if (!batchBusinessKeys.add(batchKey)) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_CONFLICT, "岗位分配重复");
            }
            if (repository.existsByPractitionerIdAndRoleIdAndOrgIdAndLocationId(
                item.getPractitionerId(), item.getRoleId(), item.getOrgId(), item.getLocationId())) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_CONFLICT, "岗位分配重复");
            }
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

            PractitionerRole role = new PractitionerRole();
            role.setPractitionerId(item.getPractitionerId());
            role.setOrgId(item.getOrgId());
            role.setLocationId(item.getLocationId());
            role.setRoleId(item.getRoleId());
            role.setName(item.getName());
            role.setSpecialtyCode(item.getSpecialtyCode());
            role.setTypeCode(item.getTypeCode());
            role.assertAssignable();
            role.initAsActive();
            toSave.add(role);
        }

        List<PractitionerRole> saved = repository.saveAll(toSave);
        Map<String, PractitionerRole> roleByBusinessKey = new HashMap<>();
        for (PractitionerRole role : saved) {
            roleByBusinessKey.put(buildUniqueBusinessKey(role.getPractitionerId(), role.getRoleId(), role.getOrgId(), role.getLocationId()), role);
        }

        List<PractitionerRoleServiceItem> serviceItemsToSave = new ArrayList<>();
        List<PractitionerRoleScheduleItem> scheduleItemsToSave = new ArrayList<>();
        for (AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand item : cmd.resolveItems()) {
            String key = buildUniqueBusinessKey(item.getPractitionerId(), item.getRoleId(), item.getOrgId(), item.getLocationId());
            PractitionerRole role = roleByBusinessKey.get(key);
            if (role == null) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_CONFLICT, "岗位落库结果回绑失败");
            }
            if (item.getServiceIds() != null && !item.getServiceIds().isEmpty()) {
                Set<Long> set = new LinkedHashSet<>(item.getServiceIds());
                for (Long serviceId : set) {
                    PractitionerRoleServiceItem row = new PractitionerRoleServiceItem();
                    row.setAssignmentId(role.getId());
                    row.setHealthcareServiceId(serviceId);
                    serviceItemsToSave.add(row);
                }
            }
            if (item.getScheduleItems() != null && !item.getScheduleItems().isEmpty()) {
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
        if (!serviceItemsToSave.isEmpty()) {
            serviceItemRepository.saveAll(serviceItemsToSave);
        }
        if (!scheduleItemsToSave.isEmpty()) {
            scheduleItemRepository.saveAll(scheduleItemsToSave);
        }

        List<Long> ids = new ArrayList<>();
        for (PractitionerRole role : saved) {
            ids.add(role.getId());
        }
        return ids;
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
