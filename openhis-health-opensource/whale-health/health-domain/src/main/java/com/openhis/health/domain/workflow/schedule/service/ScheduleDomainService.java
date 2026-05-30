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

package com.openhis.health.domain.workflow.schedule.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.workflow.schedule.ScheduleActorsReplacedEvent;
import com.openhis.health.domain.share.events.workflow.schedule.ScheduleCreatedEvent;
import com.openhis.health.domain.share.events.workflow.schedule.ScheduleDeactivatedEvent;
import com.openhis.health.domain.share.events.workflow.schedule.ScheduleIdentifiersSyncedEvent;
import com.openhis.health.domain.share.events.workflow.schedule.SchedulePlanningHorizonChangedEvent;
import com.openhis.health.domain.share.events.workflow.schedule.SchedulePublishedEvent;
import com.openhis.health.domain.share.events.workflow.schedule.ScheduleReactivatedEvent;
import com.openhis.health.domain.share.events.workflow.schedule.ScheduleRetiredEvent;
import com.openhis.health.domain.share.events.workflow.schedule.ScheduleServiceProfileUpdatedEvent;
import com.openhis.health.domain.workflow.schedule.command.AdjustPlanningHorizonCommand;
import com.openhis.health.domain.workflow.schedule.command.CreateScheduleCommand;
import com.openhis.health.domain.workflow.schedule.command.DeactivateScheduleCommand;
import com.openhis.health.domain.workflow.schedule.command.PublishScheduleCommand;
import com.openhis.health.domain.workflow.schedule.command.ReactivateScheduleCommand;
import com.openhis.health.domain.workflow.schedule.command.ReplaceScheduleActorsCommand;
import com.openhis.health.domain.workflow.schedule.command.RetireScheduleCommand;
import com.openhis.health.domain.workflow.schedule.command.SyncScheduleIdentifiersCommand;
import com.openhis.health.domain.workflow.schedule.command.UpdateScheduleCommentCommand;
import com.openhis.health.domain.workflow.schedule.command.UpdateServiceProfileCommand;
import com.openhis.health.domain.workflow.schedule.entity.Schedule;
import com.openhis.health.domain.workflow.schedule.entity.ScheduleActor;
import com.openhis.health.domain.workflow.schedule.entity.ScheduleIdentifier;
import com.openhis.health.domain.workflow.schedule.repository.ScheduleActorRepository;
import com.openhis.health.domain.workflow.schedule.repository.ScheduleIdentifierRepository;
import com.openhis.health.domain.workflow.schedule.repository.ScheduleRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * 号源日程写侧领域服务（DM-WKF-009）。
 *
 * <p>草稿创建、发布、计划窗口调整、服务口径更新、参与者与外部标识全量替换/同步、说明维护、停用/恢复/废弃；各命令批次内仅处理一条日程。</p>
 */
@Service
@RequiredArgsConstructor
public class ScheduleDomainService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleActorRepository scheduleActorRepository;
    private final ScheduleIdentifierRepository scheduleIdentifierRepository;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 创建草稿日程：单条批次内写入机构与名称，落参与者与可选外部标识后发布创建事件。
     *
     * @param command 创建项（须含 organizationId、actors）与操作人
     * @return 新日程主键
     * @throws BusinessException 批次条数不为 1、机构或参与者缺失等
     */
    public Long createSchedule(CreateScheduleCommand command) {
        List<CreateScheduleCommand.CreateScheduleItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "一次仅允许创建一条日程");
        }
        CreateScheduleCommand.CreateScheduleItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        // 业务分支处理。
        if (item.getActors() == null || item.getActors().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "actors 不能为空");
        }

        Schedule schedule = new Schedule();
        schedule.setOrganizationId(item.getOrganizationId());
        schedule.setName(item.getName());
        schedule.initDraft();
        schedule.assertActorNotEmpty(item.getActors().size());

        Schedule saved = scheduleRepository.save(schedule);

        List<ScheduleActor> actorRows = this.buildActorRows(saved.getId(), item.getActors());
        List<ScheduleIdentifier> identifierRows = this.buildIdentifierRows(saved.getId(), item.getIdentifiers());
        scheduleActorRepository.saveAll(actorRows);
        // 业务分支处理。
        if (!identifierRows.isEmpty()) {
            scheduleIdentifierRepository.saveAll(identifierRows);
        }

        domainEventPublisher.publish(new ScheduleCreatedEvent(saved.getId(), saved.getId(), saved.getOrganizationId(), command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 发布日程：校验可发布后由草稿切为启用，写入计划窗口起止并发布发布事件。
     *
     * @param command 须含 scheduleId、计划窗口与操作人
     * @throws BusinessException 批次条数、参数或聚合状态不满足发布条件时
     */
    public void publishSchedule(PublishScheduleCommand command) {
        List<PublishScheduleCommand.PublishScheduleItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "一次仅允许发布一条日程");
        }
        PublishScheduleCommand.PublishScheduleItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "scheduleId 不能为空");
        }

        Schedule schedule = this.mustGetSchedule(item.getScheduleId());
        List<ScheduleActor> actors = scheduleActorRepository.findByScheduleIdOrderBySortNoAsc(schedule.getId());
        schedule.assertPublishable(actors.size(), item.getPlanningHorizonStart(), item.getPlanningHorizonEnd());
        schedule.draftToActive(item.getPlanningHorizonStart(), item.getPlanningHorizonEnd());
        scheduleRepository.save(schedule);

        domainEventPublisher.publish(new SchedulePublishedEvent(
            schedule.getId(),
            schedule.getId(),
            schedule.getPlanningHorizonStart(),
            schedule.getPlanningHorizonEnd(),
            command.getOperatorId()));
    }

    /**
     * 调整号源计划窗口：记录旧窗口并发布窗口变更事件。
     *
     * @param command 须含 scheduleId、新起止时间与操作人
     * @throws BusinessException 批次或窗口参数非法、状态不允许调整时
     */
    public void adjustPlanningHorizon(AdjustPlanningHorizonCommand command) {
        List<AdjustPlanningHorizonCommand.AdjustPlanningHorizonItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "一次仅允许调整一条日程窗口");
        }
        AdjustPlanningHorizonCommand.AdjustPlanningHorizonItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "scheduleId 不能为空");
        }
        Schedule schedule = this.mustGetSchedule(item.getScheduleId());

        LocalDateTime oldStart = schedule.getPlanningHorizonStart();
        LocalDateTime oldEnd = schedule.getPlanningHorizonEnd();
        schedule.assertPlanningHorizonAdjustable(item.getNewStart(), item.getNewEnd());
        schedule.adjustPlanningHorizon(item.getNewStart(), item.getNewEnd());
        scheduleRepository.save(schedule);

        domainEventPublisher.publish(new SchedulePlanningHorizonChangedEvent(
            schedule.getId(), schedule.getId(), oldStart, oldEnd, schedule.getPlanningHorizonStart(), schedule.getPlanningHorizonEnd(), command.getOperatorId()));
    }

    /**
     * 更新日程服务口径（类目/专科/服务类型概念/关联医疗服务等 JSON 快照）并发布更新事件。
     *
     * @param command 须含 scheduleId、profileVersion 与口径字段及操作人
     * @throws BusinessException 批次或参数非法、口径校验失败时
     */
    public void updateServiceProfile(UpdateServiceProfileCommand command) {
        List<UpdateServiceProfileCommand.UpdateServiceProfileItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "一次仅允许更新一条日程服务口径");
        }
        UpdateServiceProfileCommand.UpdateServiceProfileItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "scheduleId 不能为空");
        }
        Schedule schedule = this.mustGetSchedule(item.getScheduleId());
        schedule.assertServiceProfileValid();
        schedule.updateServiceProfile(item.getServiceCategoryJson(), item.getSpecialtyJson(), item.getServiceTypeConceptJson(), item.getHealthcareServiceId());
        scheduleRepository.save(schedule);

        domainEventPublisher.publish(new ScheduleServiceProfileUpdatedEvent(
            schedule.getId(), schedule.getId(), item.getProfileVersion(), command.getOperatorId()));
    }

    /**
     * 全量替换日程参与者：软删旧行后按命令插入新参与者，非退役日程方可操作，发布替换事件。
     *
     * @param command 须含 scheduleId、actors 与操作人
     * @throws BusinessException 批次/参与者非法、已退役或参与者重复时
     */
    public void replaceScheduleActors(ReplaceScheduleActorsCommand command) {
        List<ReplaceScheduleActorsCommand.ReplaceScheduleActorsItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "一次仅允许替换一条日程参与者");
        }
        ReplaceScheduleActorsCommand.ReplaceScheduleActorsItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "scheduleId 不能为空");
        }
        // 业务分支处理。
        if (item.getActors() == null || item.getActors().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "actors 不能为空");
        }
        Schedule schedule = this.mustGetSchedule(item.getScheduleId());
        schedule.assertNotRetired();

        this.assertActorNotEmptyAndUnique(item.getActors());

        List<ScheduleActor> existing = scheduleActorRepository.findByScheduleIdOrderBySortNoAsc(schedule.getId());
        for (ScheduleActor row : existing) {
            row.setIsDeleted(true);
        }
        // 业务分支处理。
        if (!existing.isEmpty()) {
            scheduleActorRepository.saveAll(existing);
        }

        List<ScheduleActor> newRows = new ArrayList<>();
        for (ReplaceScheduleActorsCommand.ScheduleActorItemCommand actor : item.getActors()) {
            ScheduleActor row = new ScheduleActor();
            row.setScheduleId(schedule.getId());
            row.setActorType(actor.getActorType());
            row.setActorId(actor.getActorId());
            // 参与者未指定排序号时，默认追加到列表末尾（以当前列表长度作为顺序值）。
            if (actor.getSortNo() == null) {
                row.setSortNo(newRows.size());
            } else {
                row.setSortNo(actor.getSortNo());
            }
            newRows.add(row);
        }
        scheduleActorRepository.saveAll(newRows);

        domainEventPublisher.publish(new ScheduleActorsReplacedEvent(schedule.getId(), schedule.getId(), newRows.size(), command.getOperatorId()));
    }

    /**
     * 全量同步日程外部标识：软删旧标识后写入新列表，非退役方可操作，发布同步事件。
     *
     * @param command 须含 scheduleId、identifiers 与操作人
     * @throws BusinessException 批次非法、标识重复或日程已退役时
     */
    public void syncScheduleIdentifiers(SyncScheduleIdentifiersCommand command) {
        List<SyncScheduleIdentifiersCommand.SyncScheduleIdentifiersItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "一次仅允许同步一条日程标识");
        }
        SyncScheduleIdentifiersCommand.SyncScheduleIdentifiersItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "scheduleId 不能为空");
        }
        Schedule schedule = this.mustGetSchedule(item.getScheduleId());
        schedule.assertNotRetired();

        this.assertIdentifierUnique(item.getIdentifiers());

        List<ScheduleIdentifier> existing = scheduleIdentifierRepository.findByScheduleId(schedule.getId());
        for (ScheduleIdentifier row : existing) {
            row.setIsDeleted(true);
        }
        // 业务分支处理。
        if (!existing.isEmpty()) {
            scheduleIdentifierRepository.saveAll(existing);
        }

        List<ScheduleIdentifier> newRows = this.buildIdentifierRowsForSync(schedule.getId(), item.getIdentifiers());
        // 业务分支处理。
        if (!newRows.isEmpty()) {
            scheduleIdentifierRepository.saveAll(newRows);
        }

        domainEventPublisher.publish(new ScheduleIdentifiersSyncedEvent(schedule.getId(), schedule.getId(), newRows.size(), command.getOperatorId()));
    }

    /**
     * 更新日程说明（Markdown）：先校验长度再写入。
     *
     * @param command 须含 scheduleId、commentMarkdown
     * @throws BusinessException 批次条数不为 1、参数缺失或超长时
     */
    public void updateComment(UpdateScheduleCommentCommand command) {
        List<UpdateScheduleCommentCommand.UpdateScheduleCommentItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "一次仅允许更新一条日程说明");
        }
        UpdateScheduleCommentCommand.UpdateScheduleCommentItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "scheduleId 不能为空");
        }
        Schedule schedule = this.mustGetSchedule(item.getScheduleId());
        schedule.assertCommentLength(item.getCommentMarkdown());
        schedule.updateComment(item.getCommentMarkdown());
        scheduleRepository.save(schedule);
    }

    /**
     * 停用日程（启用→停用），发布停用事件（含原因）。
     *
     * @param command 须含 scheduleId、reason 与操作人
     * @throws BusinessException 不可停用或参数非法时
     */
    public void deactivateSchedule(DeactivateScheduleCommand command) {
        List<DeactivateScheduleCommand.DeactivateScheduleItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "一次仅允许停用一条日程");
        }
        DeactivateScheduleCommand.DeactivateScheduleItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "scheduleId 不能为空");
        }
        Schedule schedule = this.mustGetSchedule(item.getScheduleId());
        schedule.assertDeactivatable();
        schedule.activeToInactive();
        scheduleRepository.save(schedule);

        domainEventPublisher.publish(new ScheduleDeactivatedEvent(schedule.getId(), schedule.getId(), item.getReason(), command.getOperatorId()));
    }

    /**
     * 恢复启用日程（停用→启用），发布恢复事件。
     *
     * @param command 须含 scheduleId 与操作人
     * @throws BusinessException 不可恢复或参数非法时
     */
    public void reactivateSchedule(ReactivateScheduleCommand command) {
        List<ReactivateScheduleCommand.ReactivateScheduleItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "一次仅允许恢复一条日程");
        }
        ReactivateScheduleCommand.ReactivateScheduleItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "scheduleId 不能为空");
        }
        Schedule schedule = this.mustGetSchedule(item.getScheduleId());
        schedule.assertReactivatable();
        schedule.inactiveToActive();
        scheduleRepository.save(schedule);

        domainEventPublisher.publish(new ScheduleReactivatedEvent(schedule.getId(), schedule.getId(), command.getOperatorId()));
    }

    /**
     * 废弃日程（终态），发布废弃事件（含原因）。
     *
     * @param command 须含 scheduleId、reason 与操作人
     * @throws BusinessException 不可废弃或参数非法时
     */
    public void retireSchedule(RetireScheduleCommand command) {
        List<RetireScheduleCommand.RetireScheduleItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "一次仅允许废弃一条日程");
        }
        RetireScheduleCommand.RetireScheduleItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "scheduleId 不能为空");
        }
        Schedule schedule = this.mustGetSchedule(item.getScheduleId());
        schedule.assertRetirable();
        schedule.toRetired();
        scheduleRepository.save(schedule);

        domainEventPublisher.publish(new ScheduleRetiredEvent(schedule.getId(), schedule.getId(), item.getReason(), command.getOperatorId()));
    }

    /**
     * 按主键加载日程，不存在则抛出业务异常。
     *
     * @param id 日程主键
     * @return 日程实体
     * @throws BusinessException 日程不存在时
     */
    private Schedule mustGetSchedule(Long id) {
        return scheduleRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.SCHEDULE_NOT_FOUND, "号源日程不存在"));
    }

    /**
     * 将创建命令中的参与者列表转为待保存的 {@link ScheduleActor} 行。
     *
     * <p>遍历参与者列表，校验必填字段，设置默认排序号，构建待保存实体列表。</p>
     *
     * @param scheduleId 日程主键
     * @param actors     参与者命令列表
     * @return 待保存的参与者实体列表
     * @throws BusinessException actorType 或 actorId 为空时
     */
    private List<ScheduleActor> buildActorRows(Long scheduleId, List<CreateScheduleCommand.ScheduleActorItemCommand> actors) {
        List<ScheduleActor> rows = new ArrayList<>();
        int i = 0;
        // Loop：遍历参与者命令列表构建实体。
        for (CreateScheduleCommand.ScheduleActorItemCommand actor : actors) {
            if (actor.getActorType() == null || actor.getActorId() == null) {
                throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "actorType/actorId 不能为空");
            }
            // Asm：构建参与者实体。
            ScheduleActor row = new ScheduleActor();
            row.setScheduleId(scheduleId);
            row.setActorType(actor.getActorType());
            row.setActorId(actor.getActorId());
            // 业务分支处理：未指定排序号时按顺序自动分配。
            if (actor.getSortNo() == null) {
                row.setSortNo(i);
            } else {
                row.setSortNo(actor.getSortNo());
            }
            rows.add(row);
            i++;
        }
        return rows;
    }

    /**
     * 将创建命令中的外部标识列表转为待保存的 {@link ScheduleIdentifier} 行。
     *
     * @param scheduleId  日程主键
     * @param identifiers 外部标识命令列表
     * @return 待保存的外部标识实体列表
     * @throws BusinessException idSystem 或 idValue 为空时
     */
    private List<ScheduleIdentifier> buildIdentifierRows(Long scheduleId, List<CreateScheduleCommand.ScheduleIdentifierItemCommand> identifiers) {
        List<ScheduleIdentifier> rows = new ArrayList<>();
        // 业务分支处理：空列表直接返回。
        if (identifiers == null || identifiers.isEmpty()) {
            return rows;
        }
        // Loop：遍历标识列表构建实体。
        for (CreateScheduleCommand.ScheduleIdentifierItemCommand id : identifiers) {
            if (id.getIdSystem() == null || id.getIdSystem().trim().isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "idSystem 不能为空");
            }
            if (id.getIdValue() == null || id.getIdValue().trim().isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "idValue 不能为空");
            }
            // Asm：构建标识实体。
            ScheduleIdentifier row = new ScheduleIdentifier();
            row.setScheduleId(scheduleId);
            row.setIdSystem(id.getIdSystem().trim());
            row.setIdValue(id.getIdValue().trim());
            row.setUseCode(id.getUseCode());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 将同步命令中的外部标识列表转为待保存的 {@link ScheduleIdentifier} 行。
     *
     * @param scheduleId  日程主键
     * @param identifiers 外部标识命令列表
     * @return 待保存的外部标识实体列表
     * @throws BusinessException idSystem 或 idValue 为空时
     */
    private List<ScheduleIdentifier> buildIdentifierRowsForSync(Long scheduleId, List<SyncScheduleIdentifiersCommand.ScheduleIdentifierItemCommand> identifiers) {
        List<ScheduleIdentifier> rows = new ArrayList<>();
        // 业务分支处理：空列表直接返回。
        if (identifiers == null || identifiers.isEmpty()) {
            return rows;
        }
        // Loop：遍历标识列表构建实体。
        for (SyncScheduleIdentifiersCommand.ScheduleIdentifierItemCommand id : identifiers) {
            if (id.getIdSystem() == null || id.getIdSystem().trim().isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "idSystem 不能为空");
            }
            if (id.getIdValue() == null || id.getIdValue().trim().isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "idValue 不能为空");
            }
            // Asm：构建标识实体。
            ScheduleIdentifier row = new ScheduleIdentifier();
            row.setScheduleId(scheduleId);
            row.setIdSystem(id.getIdSystem().trim());
            row.setIdValue(id.getIdValue().trim());
            row.setUseCode(id.getUseCode());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 校验参与者非空且 (actorType, actorId) 组合不重复。
     *
     * @param actors 参与者命令列表
     * @throws BusinessException 列表为空、actorType/actorId 为空或组合重复时
     */
    private void assertActorNotEmptyAndUnique(List<ReplaceScheduleActorsCommand.ScheduleActorItemCommand> actors) {
        if (actors == null || actors.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "actors 不能为空");
        }
        Set<String> unique = new HashSet<>();
        // Loop：遍历参与者列表校验必填和唯一性。
        for (ReplaceScheduleActorsCommand.ScheduleActorItemCommand actor : actors) {
            if (actor.getActorType() == null || actor.getActorId() == null) {
                throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "actorType/actorId 不能为空");
            }
            // 使用特殊分隔符构建唯一键。
            String key = actor.getActorType() + "\u0001" + actor.getActorId();
            if (unique.contains(key)) {
                throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "actor 组合不允许重复");
            }
            unique.add(key);
        }
    }

    /**
     * 校验外部标识列表内 (idSystem, idValue) 不重复。
     *
     * @param identifiers 外部标识命令列表
     * @throws BusinessException idSystem/idValue 为空或组合重复时
     */
    private void assertIdentifierUnique(List<SyncScheduleIdentifiersCommand.ScheduleIdentifierItemCommand> identifiers) {
        // 业务分支处理：空列表直接通过校验。
        if (identifiers == null || identifiers.isEmpty()) {
            return;
        }
        Set<String> unique = new HashSet<>();
        // Loop：遍历标识列表校验必填和唯一性。
        for (SyncScheduleIdentifiersCommand.ScheduleIdentifierItemCommand id : identifiers) {
            if (id.getIdSystem() == null || id.getIdSystem().trim().isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "idSystem 不能为空");
            }
            if (id.getIdValue() == null || id.getIdValue().trim().isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "idValue 不能为空");
            }
            // 使用特殊分隔符构建唯一键。
            String key = id.getIdSystem().trim() + "\u0001" + id.getIdValue().trim();
            if (unique.contains(key)) {
                throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "identifier 不允许重复");
            }
            unique.add(key);
        }
    }
}
