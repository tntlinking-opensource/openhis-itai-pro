/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.schedule.service;

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
import com.openhis.whale.booking.domain.schedule.command.AdjustPlanningHorizonCommand;
import com.openhis.whale.booking.domain.schedule.command.CreateScheduleCommand;
import com.openhis.whale.booking.domain.schedule.command.DeactivateScheduleCommand;
import com.openhis.whale.booking.domain.schedule.command.PublishScheduleCommand;
import com.openhis.whale.booking.domain.schedule.command.ReactivateScheduleCommand;
import com.openhis.whale.booking.domain.schedule.command.ReplaceScheduleActorsCommand;
import com.openhis.whale.booking.domain.schedule.command.RetireScheduleCommand;
import com.openhis.whale.booking.domain.schedule.command.SubstitutePractitionerCommand;
import com.openhis.whale.booking.domain.schedule.command.SyncScheduleIdentifiersCommand;
import com.openhis.whale.booking.domain.schedule.command.UpdateScheduleCommentCommand;
import com.openhis.whale.booking.domain.schedule.command.UpdateServiceProfileCommand;
import com.openhis.whale.booking.domain.schedule.entity.Schedule;
import com.openhis.whale.booking.domain.schedule.entity.ScheduleActor;
import com.openhis.whale.booking.domain.schedule.entity.ScheduleIdentifier;
import com.openhis.whale.booking.domain.schedule.support.ScheduleActorCompositionRules;
import com.openhis.whale.booking.domain.schedule.support.ScheduleActorCompositionRules.ScheduleActorRef;
import com.openhis.whale.booking.domain.schedule.repository.ScheduleActorRepository;
import com.openhis.whale.booking.domain.schedule.repository.ScheduleIdentifierRepository;
import com.openhis.whale.booking.domain.schedule.repository.ScheduleRepository;
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
 * <p>聚合边界：{@link Schedule} 主根 + {@link ScheduleActor} / {@link ScheduleIdentifier} 子实体。</p>
 * <p>不变量摘要：草稿→发布须绑定规划窗口；参与者须满足门诊组合规则（BR-SCH-001）；已废弃不可写；
 * 外部标识 (idSystem,idValue) 唯一；事件在 {@code save} 之后发布。</p>
 * <p>写入口：创建、发布、窗口调整、服务口径、参与者/标识全量替换、说明、停用/恢复/废弃、替诊。</p>
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
        // 前置校验：命令批次须恰好一条（单聚合写入口）。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "一次仅允许创建一条日程");
        }
        CreateScheduleCommand.CreateScheduleItemCommand item = items.get(0);
        // 前置校验：机构主键必填，日程须归属院点。
        if (item.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        // 前置校验：门诊参与者组合须含医师+号源类型等（BR-SCH-001）。
        this.assertOutpatientActorCompositionFromCreate(item.getActors());

        Schedule schedule = new Schedule();
        schedule.setOrganizationId(item.getOrganizationId());
        schedule.setName(item.getName());
        schedule.setSourceScheduleTemplateId(item.getSourceScheduleTemplateId());
        schedule.setSlotProvisionTemplateId(item.getSlotProvisionTemplateId());
        schedule.initDraft();
        schedule.assertActorNotEmpty(item.getActors().size());

        // 持久化：先落 Schedule 头，再落参与者与可选外部标识。
        Schedule saved = scheduleRepository.save(schedule);

        List<ScheduleActor> actorRows = this.buildActorRows(saved.getId(), item.getActors());
        List<ScheduleIdentifier> identifierRows = this.buildIdentifierRows(saved.getId(), item.getIdentifiers());
        scheduleActorRepository.saveAll(actorRows);
        // 可选：创建时携带的外部标识一并写入。
        if (!identifierRows.isEmpty()) {
            scheduleIdentifierRepository.saveAll(identifierRows);
        }

        // 发布事件：创建完成后通知下游索引/同步。
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
        // 前置校验：命令批次须恰好一条（单聚合写入口）。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "一次仅允许发布一条日程");
        }
        PublishScheduleCommand.PublishScheduleItemCommand item = items.get(0);
        // 前置校验：scheduleId 为空则无法加载聚合根。
        if (item.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "scheduleId 不能为空");
        }

        Schedule schedule = this.mustGetSchedule(item.getScheduleId());
        List<ScheduleActor> actors = scheduleActorRepository.findByScheduleIdOrderBySortNoAsc(schedule.getId());
        this.assertOutpatientActorCompositionFromEntities(actors);
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
        // 前置校验：命令批次须恰好一条（单聚合写入口）。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "一次仅允许调整一条日程窗口");
        }
        AdjustPlanningHorizonCommand.AdjustPlanningHorizonItemCommand item = items.get(0);
        // 前置校验：scheduleId 为空则无法加载聚合根。
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
        // 前置校验：命令批次须恰好一条（单聚合写入口）。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "一次仅允许更新一条日程服务口径");
        }
        UpdateServiceProfileCommand.UpdateServiceProfileItemCommand item = items.get(0);
        // 前置校验：scheduleId 为空则无法加载聚合根。
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
        // 前置校验：命令批次须恰好一条（单聚合写入口）。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "一次仅允许替换一条日程参与者");
        }
        ReplaceScheduleActorsCommand.ReplaceScheduleActorsItemCommand item = items.get(0);
        // 前置校验：scheduleId 为空则无法加载聚合根。
        if (item.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "scheduleId 不能为空");
        }
        // 前置校验：加载日程并拒绝已废弃聚合上的参与者替换。
        Schedule schedule = this.mustGetSchedule(item.getScheduleId());
        schedule.assertNotRetired();

        this.assertOutpatientActorCompositionFromReplace(item.getActors());

        List<ScheduleActor> existing = scheduleActorRepository.findByScheduleIdOrderBySortNoAsc(schedule.getId());
        for (ScheduleActor row : existing) {
            row.setIsDeleted(true);
        }
        // 持久化：软删旧参与者行，再写入全量新参与者。
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
        // 前置校验：命令批次须恰好一条（单聚合写入口）。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "一次仅允许同步一条日程标识");
        }
        SyncScheduleIdentifiersCommand.SyncScheduleIdentifiersItemCommand item = items.get(0);
        // 前置校验：scheduleId 为空则无法加载聚合根。
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
        // 持久化：软删旧外部标识行。
        if (!existing.isEmpty()) {
            scheduleIdentifierRepository.saveAll(existing);
        }

        List<ScheduleIdentifier> newRows = this.buildIdentifierRowsForSync(schedule.getId(), item.getIdentifiers());
        // 持久化：写入同步后的新标识列表（可为空表示清空）。
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
        // 前置校验：命令批次须恰好一条（单聚合写入口）。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "一次仅允许更新一条日程说明");
        }
        UpdateScheduleCommentCommand.UpdateScheduleCommentItemCommand item = items.get(0);
        // 前置校验：scheduleId 为空则无法加载聚合根。
        if (item.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "scheduleId 不能为空");
        }
        Schedule schedule = this.mustGetSchedule(item.getScheduleId());
        schedule.assertCommentLength(item.getCommentMarkdown());
        schedule.updateComment(item.getCommentMarkdown());
        scheduleRepository.save(schedule);
    }

    /**
     * 更新日程显示名称（草稿编辑等场景）。
     *
     * @param scheduleId 日程主键
     * @param name 名称
     */
    public void updateName(Long scheduleId, String name) {
        if (scheduleId == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "scheduleId 不能为空");
        }
        Schedule schedule = this.mustGetSchedule(scheduleId);
        schedule.updateName(name);
        scheduleRepository.save(schedule);
    }

    /**
     * 更新草稿的模板血缘快照（T2/T1 引用）。
     *
     * @param scheduleId 日程主键
     * @param sourceScheduleTemplateId 来源 T2 主键
     * @param slotProvisionTemplateId 关联 T1 主键
     */
    public void updateTemplateSnapshot(Long scheduleId, Long sourceScheduleTemplateId, Long slotProvisionTemplateId) {
        if (scheduleId == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "scheduleId 不能为空");
        }
        Schedule schedule = this.mustGetSchedule(scheduleId);
        schedule.updateTemplateSnapshot(sourceScheduleTemplateId, slotProvisionTemplateId);
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
        // 前置校验：命令批次须恰好一条（单聚合写入口）。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "一次仅允许停用一条日程");
        }
        DeactivateScheduleCommand.DeactivateScheduleItemCommand item = items.get(0);
        // 前置校验：scheduleId 为空则无法加载聚合根。
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
     * 替诊：设置或撤销顶班医师，不修改 Actor、不迁移预约（2.21）。
     */
    public void substitutePractitioner(SubstitutePractitionerCommand command) {
        List<SubstitutePractitionerCommand.SubstitutePractitionerItemCommand> items = command.resolveItems();
        // 前置校验：命令批次须恰好一条（单聚合写入口）。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "一次仅允许替诊一条日程");
        }
        SubstitutePractitionerCommand.SubstitutePractitionerItemCommand item = items.get(0);
        // 前置校验：scheduleId 为空则无法加载聚合根。
        if (item.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "scheduleId 不能为空");
        }
        Schedule schedule = this.mustGetSchedule(item.getScheduleId());
        // 替诊 id 为空表示撤销顶班；否则写入 substitute 字段（Actor 仍保留原医师）。
        if (item.getSubstitutePractitionerId() == null) {
            schedule.clearSubstitute();
        } else {
            schedule.applySubstitute(item.getSubstitutePractitionerId(), item.getReason());
        }
        scheduleRepository.save(schedule);
    }

    /**
     * 恢复启用日程（停用→启用），发布恢复事件。
     *
     * @param command 须含 scheduleId 与操作人
     * @throws BusinessException 不可恢复或参数非法时
     */
    public void reactivateSchedule(ReactivateScheduleCommand command) {
        List<ReactivateScheduleCommand.ReactivateScheduleItemCommand> items = command.resolveItems();
        // 前置校验：命令批次须恰好一条（单聚合写入口）。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "一次仅允许恢复一条日程");
        }
        ReactivateScheduleCommand.ReactivateScheduleItemCommand item = items.get(0);
        // 前置校验：scheduleId 为空则无法加载聚合根。
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
        // 前置校验：命令批次须恰好一条（单聚合写入口）。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "一次仅允许废弃一条日程");
        }
        RetireScheduleCommand.RetireScheduleItemCommand item = items.get(0);
        // 前置校验：scheduleId 为空则无法加载聚合根。
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
            // 未指定 sortNo 时按列表顺序自动编号，保证展示稳定。
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
        // 无外部标识时跳过构建，避免无意义 insert。
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
        // 无外部标识时跳过构建，避免无意义 insert。
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
     * 创建命令参与者 → v2 组合校验。
     */
    private void assertOutpatientActorCompositionFromCreate(List<CreateScheduleCommand.ScheduleActorItemCommand> actors) {
        List<ScheduleActorRef> refs = new ArrayList<>();
        if (actors != null) {
            for (CreateScheduleCommand.ScheduleActorItemCommand actor : actors) {
                refs.add(new ScheduleActorRef(actor.getActorType(), actor.getActorId()));
            }
        }
        ScheduleActorCompositionRules.assertOutpatientComposition(refs);
    }

    /**
     * 替换命令参与者 → v2 组合校验。
     */
    private void assertOutpatientActorCompositionFromReplace(List<ReplaceScheduleActorsCommand.ScheduleActorItemCommand> actors) {
        List<ScheduleActorRef> refs = new ArrayList<>();
        if (actors != null) {
            for (ReplaceScheduleActorsCommand.ScheduleActorItemCommand actor : actors) {
                refs.add(new ScheduleActorRef(actor.getActorType(), actor.getActorId()));
            }
        }
        ScheduleActorCompositionRules.assertOutpatientComposition(refs);
    }

    /**
     * 已持久化参与者 → v2 组合校验（发布前）。
     */
    private void assertOutpatientActorCompositionFromEntities(List<ScheduleActor> actors) {
        List<ScheduleActorRef> refs = new ArrayList<>();
        if (actors != null) {
            for (ScheduleActor actor : actors) {
                refs.add(new ScheduleActorRef(actor.getActorType(), actor.getActorId()));
            }
        }
        ScheduleActorCompositionRules.assertOutpatientComposition(refs);
    }

    /**
     * 校验外部标识列表内 (idSystem, idValue) 不重复。
     *
     * @param identifiers 外部标识命令列表
     * @throws BusinessException idSystem/idValue 为空或组合重复时
     */
    private void assertIdentifierUnique(List<SyncScheduleIdentifiersCommand.ScheduleIdentifierItemCommand> identifiers) {
        // 空标识列表视为合法（同步时可清空全部外部标识）。
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
