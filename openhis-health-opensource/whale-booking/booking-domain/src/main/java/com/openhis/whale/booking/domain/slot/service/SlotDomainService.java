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

package com.openhis.whale.booking.domain.slot.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.workflow.slot.SlotBatchProvisionedEvent;
import com.openhis.health.domain.share.events.workflow.slot.SlotBlockedEvent;
import com.openhis.health.domain.share.events.workflow.slot.SlotBookedEvent;
import com.openhis.health.domain.share.events.workflow.slot.SlotEnteredInErrorEvent;
import com.openhis.health.domain.share.events.workflow.slot.SlotIdentifiersSyncedEvent;
import com.openhis.health.domain.share.events.workflow.slot.SlotOverbookedFlagSetEvent;
import com.openhis.health.domain.share.events.workflow.slot.SlotReleasedEvent;
import com.openhis.health.domain.share.events.workflow.slot.SlotServiceOverrideUpdatedEvent;
import com.openhis.health.domain.share.events.workflow.slot.SlotTentativeConfirmedEvent;
import com.openhis.health.domain.share.events.workflow.slot.SlotTentativeMarkedEvent;
import com.openhis.health.domain.share.events.workflow.slot.SlotUnblockedEvent;
import com.openhis.whale.booking.domain.stopclinic.service.StopClinicSlotFreezeDomainService;
import com.openhis.whale.booking.domain.slot.command.AllocateBookingCommand;
import com.openhis.whale.booking.domain.slot.command.BlockSlotCommand;
import com.openhis.whale.booking.domain.slot.command.ConfirmTentativeCommand;
import com.openhis.whale.booking.domain.slot.command.MarkEnteredInErrorCommand;
import com.openhis.whale.booking.domain.slot.command.MarkTentativeCommand;
import com.openhis.whale.booking.domain.slot.command.ProvisionSlotBatchCommand;
import com.openhis.whale.booking.domain.slot.command.ReleaseBookingCommand;
import com.openhis.whale.booking.domain.slot.command.RetireSlotCommand;
import com.openhis.whale.booking.domain.slot.command.SetOverbookedCommand;
import com.openhis.whale.booking.domain.slot.command.SyncSlotIdentifiersCommand;
import com.openhis.whale.booking.domain.slot.command.UnblockSlotCommand;
import com.openhis.whale.booking.domain.slot.command.UpdateOverrideServiceSnapshotsCommand;
import com.openhis.whale.booking.domain.slot.command.UpdateSlotCommentCommand;
import com.openhis.whale.booking.domain.slot.entity.Slot;
import com.openhis.whale.booking.domain.slot.entity.SlotIdentifier;
import com.openhis.whale.booking.domain.slot.repository.SlotIdentifierRepository;
import com.openhis.whale.booking.domain.slot.repository.SlotRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * 号源时段写侧领域服务（DM-WKF-010）。
 *
 * <p>聚合边界：{@link Slot} 为主聚合根，{@link SlotIdentifier} 为其外部标识子实体；
 * 本服务负责批量出号、封锁/解封、占用/释放、暂定确认、差错标记、标识同步与服务快照维护。</p>
 *
 * <p>核心不变量：</p>
 * <ul>
 *   <li>同一日程下时间片不可相交重叠（出号前批内与存量均校验）</li>
 *   <li>bookedCount 不得超过 capacity（超售须显式 allowOverbook 或手工标记）</li>
 *   <li>停诊冻结时段禁止新增占用（markTentative / allocate / confirmTentative）</li>
 *   <li>entered-in-error 状态禁止标识同步等写操作</li>
 *   <li>各命令批次仅允许单条聚合写入口（单 slot 或单 schedule 出号批次）</li>
 *   <li>领域事件须在持久化成功之后发布</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SlotDomainService {

    private final SlotRepository slotRepository;
    private final SlotIdentifierRepository slotIdentifierRepository;
    private final IDomainEventPublisher domainEventPublisher;
    private final StopClinicSlotFreezeDomainService stopClinicSlotFreezeDomainService;

    /**
     * 批量生成号源时段：校验时间窗不重叠后落库主表与标识子表，并发布批量出号事件。
     *
     * @param command 含操作人、日程标识、机构标识与出号模板列表
     * @return 新建时段主键列表（按落库顺序）
     * @throws BusinessException 批次条数不为 1、必填项缺失、时间窗非法或与存量重叠时
     */
    public List<Long> provisionSlotBatch(ProvisionSlotBatchCommand command) {
        // 前置校验：操作人必填，用于审计追溯。
        this.assertOperatorIdPresent(command.getOperatorId());
        List<ProvisionSlotBatchCommand.ProvisionSlotBatchItemCommand> items = command.resolveItems();
        // 前置校验：命令批次须恰好一条（单聚合写入口）。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "一次仅允许批量生成一条日程的时段");
        }
        ProvisionSlotBatchCommand.ProvisionSlotBatchItemCommand item = items.get(0);
        // 前置校验：scheduleId 不能为空，否则无法归属日程。
        if (item.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "scheduleId 不能为空");
        }
        // 前置校验：organizationId 不能为空，保证多租户数据隔离。
        if (item.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        // 前置校验：出号模板列表不能为空，否则无时间片可生成。
        if (item.getSlotTemplates() == null || item.getSlotTemplates().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotTemplates 不能为空");
        }

        // 前置校验：批内时间窗口合法且互不重叠，避免依赖 DB 约束才报错。
        List<ProvisionSlotBatchCommand.SlotTemplateItemCommand> templates = item.getSlotTemplates();
        this.assertBatchTemplatesWindowValidAndNonOverlap(templates);

        // Pre：按批次最小/最大时间窗一次性查询潜在相交存量，避免循环 I/O。
        LocalDateTime minStart = templates.get(0).getStartTime();
        LocalDateTime maxEnd = templates.get(0).getEndTime();
        for (ProvisionSlotBatchCommand.SlotTemplateItemCommand template : templates) {
            // 遍历模板：收缩批次最小起始时刻。
            if (template.getStartTime().isBefore(minStart)) {
                minStart = template.getStartTime();
            }
            // 遍历模板：扩张批次最大结束时刻。
            if (template.getEndTime().isAfter(maxEnd)) {
                maxEnd = template.getEndTime();
            }
        }
        List<Slot> existingIntersectSlots = slotRepository.findByScheduleIdAndStartTimeLessThanAndEndTimeGreaterThan(
            item.getScheduleId(), maxEnd, minStart);

        // 前置校验：以排序+单向扫描做“模板 vs 存量”重叠校验，避免 O(n*m) 嵌套循环。
        List<ProvisionSlotBatchCommand.SlotTemplateItemCommand> sortedTemplates = new ArrayList<>(templates);
        sortedTemplates.sort((a, b) -> a.getStartTime().compareTo(b.getStartTime()));
        existingIntersectSlots.sort((a, b) -> a.getStartTime().compareTo(b.getStartTime()));
        int existingCursor = 0;
        for (ProvisionSlotBatchCommand.SlotTemplateItemCommand template : sortedTemplates) {
            while (existingCursor < existingIntersectSlots.size()
                && !existingIntersectSlots.get(existingCursor).getEndTime().isAfter(template.getStartTime())) {
                existingCursor++;
            }
            int probe = existingCursor;
            while (probe < existingIntersectSlots.size()
                && existingIntersectSlots.get(probe).getStartTime().isBefore(template.getEndTime())) {
                Slot existing = existingIntersectSlots.get(probe);
                // 存量相交探测：与待出号时间窗重叠则禁止重复出号。
                if (this.isTimeWindowIntersect(template.getStartTime(), template.getEndTime(), existing.getStartTime(), existing.getEndTime())) {
                    throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "存在与存量时段重叠的时间片，禁止出号");
                }
                probe++;
            }
        }

        // 组装：按模板初始化 Slot 聚合根并设置服务覆盖快照。
        List<Slot> slotRows = new ArrayList<>();
        List<SlotIdentifier> identifierRows = new ArrayList<>();
        for (ProvisionSlotBatchCommand.SlotTemplateItemCommand template : templates) {
            Slot slot = new Slot();
            slot.initFree(item.getOrganizationId(), item.getScheduleId(), template.getStartTime(), template.getEndTime(), template.getCapacity());
            slot.updateOverrideServiceSnapshots(
                template.getServiceCategoryJson(),
                template.getServiceTypeJson(),
                template.getSpecialtyJson(),
                template.getAppointmentTypeJson());
            slotRows.add(slot);
        }

        // 持久化：先写主表再写子表，确保子表外键引用的 slotId 已生成。
        List<Slot> savedSlots = slotRepository.saveAll(slotRows);
        Map<String, Slot> templateKeyToSavedSlot = this.buildTemplateKeyToSavedSlot(savedSlots);

        for (ProvisionSlotBatchCommand.SlotTemplateItemCommand template : templates) {
            // 模板无外部标识则跳过子表写入。
            if (template.getIdentifiers() == null || template.getIdentifiers().isEmpty()) {
                continue;
            }
            Slot savedSlot = templateKeyToSavedSlot.get(this.buildTemplateKey(template.getStartTime(), template.getEndTime()));
            // 模板与落库时段映射失败，属内部一致性错误。
            if (savedSlot == null) {
                throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "批量出号内部映射失败");
            }
            identifierRows.addAll(this.buildIdentifierRows(savedSlot.getId(), template.getIdentifiers()));
        }
        // 持久化：批量写入外部标识子表。
        if (!identifierRows.isEmpty()) {
            slotIdentifierRepository.saveAll(identifierRows);
        }

        // 发布事件：批量出号落库成功后再通知下游。
        List<Long> savedIds = new ArrayList<>();
        for (Slot saved : savedSlots) {
            savedIds.add(saved.getId());
        }
        domainEventPublisher.publish(new SlotBatchProvisionedEvent(
            savedIds.get(0),
            item.getScheduleId(),
            savedIds.size(),
            savedIds, command.getOperatorId()));
        return savedIds;
    }

    /**
     * 封锁号源时段：将 free/busy 态迁移为 busy-unavailable 并发布封锁事件。
     *
     * @param command 含 slotId、封锁原因与操作人
     * @throws BusinessException 批次条数不为 1、slotId 为空或时段不存在时
     */
    public void blockSlot(BlockSlotCommand command) {
        // 前置校验：操作人必填。
        this.assertOperatorIdPresent(command.getOperatorId());
        BlockSlotCommand.BlockSlotItemCommand item = this.resolveSingleItem(command.resolveItems(), "一次仅允许封锁一个时段");
        // 前置校验：slotId 不能为空，否则无法定位聚合根。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }

        // 加载聚合：按主键加载未软删时段。
        Slot slot = this.mustGetSlot(item.getSlotId());
        // 状态迁移：free/busy → busy-unavailable（管理员封锁）。
        slot.toBusyUnavailable(item.getReason());
        // 持久化：保存封锁后状态。
        slotRepository.save(slot);

        // 发布事件：封锁落库成功后再通知下游。
        domainEventPublisher.publish(new SlotBlockedEvent(
            slot.getId(),
            slot.getId(),
            slot.getScheduleId(),
            slot.getStartTime(),
            slot.getEndTime(), command.getOperatorId()));
    }

    /**
     * 解封号源时段：将 busy-unavailable 恢复为 free 或 busy 并发布解封事件。
     *
     * @param command 含 slotId 与操作人
     * @throws BusinessException 批次条数不为 1、slotId 为空或时段不存在时
     */
    public void unblockSlot(UnblockSlotCommand command) {
        // 前置校验：操作人必填。
        this.assertOperatorIdPresent(command.getOperatorId());
        UnblockSlotCommand.UnblockSlotItemCommand item = this.resolveSingleItem(command.resolveItems(), "一次仅允许解封一个时段");
        // 前置校验：slotId 不能为空，否则无法定位聚合根。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }

        // 加载聚合：按主键加载未软删时段。
        Slot slot = this.mustGetSlot(item.getSlotId());
        // 状态迁移：busy-unavailable → free 或 busy（按当前占用情况恢复）。
        slot.busyUnavailableToFreeOrBusy();
        // 持久化：保存解封后状态。
        slotRepository.save(slot);

        // 发布事件：解封落库成功后再通知下游。
        domainEventPublisher.publish(new SlotUnblockedEvent(
            slot.getId(),
            slot.getId(),
            slot.getScheduleId(),
            slot.getStatusCode(), command.getOperatorId()));
    }

    /**
     * 删除（软删）号源时段：标记聚合根为已废弃，不发布领域事件。
     *
     * @param command 含 slotId 与操作人
     * @throws BusinessException 批次条数不为 1、slotId 为空或时段不存在时
     */
    public void retireSlot(RetireSlotCommand command) {
        // 前置校验：操作人必填。
        this.assertOperatorIdPresent(command.getOperatorId());
        RetireSlotCommand.RetireSlotItemCommand item = this.resolveSingleItem(command.resolveItems(), "一次仅允许删除一个时段");
        // 前置校验：slotId 不能为空，否则无法定位聚合根。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }

        // 加载聚合：按主键加载未软删时段。
        Slot slot = this.mustGetSlot(item.getSlotId());
        // 状态迁移：软删时段，后续不可再被预约占用。
        slot.retire();
        // 持久化：保存软删标记。
        slotRepository.save(slot);
    }

    /**
     * 记预约占用：递增 bookedCount 并刷新 busy 状态，必要时追加超售事件。
     *
     * @param command 含 slotId、是否允许超售与操作人；tentative 须走 markTentative 路径
     * @throws BusinessException 批次条数不为 1、slotId 为空、停诊冻结、tentative=true 或容量不足时
     */
    public void allocateBooking(AllocateBookingCommand command) {
        // 前置校验：操作人必填。
        this.assertOperatorIdPresent(command.getOperatorId());
        AllocateBookingCommand.AllocateBookingItemCommand item = this.resolveSingleItem(command.resolveItems(), "一次仅允许占用一个时段");
        // 前置校验：slotId 不能为空，否则无法定位聚合根。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }

        // 加载聚合：按主键加载未软删时段。
        Slot slot = this.mustGetSlot(item.getSlotId());
        // 前置校验：停诊冻结时段禁止新增占用。
        stopClinicSlotFreezeDomainService.assertSlotNotFrozenForNewBooking(item.getSlotId());
        // 前置校验：allocateBooking 不走暂定路径，须先 markTentative 再 confirmTentative。
        if (Boolean.TRUE.equals(item.getTentative())) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "allocateBooking 不支持 tentative=true，请先 markTentative 再 confirmTentative");
        }

        Boolean oldOverbooked = slot.getOverbookedFlag();
        // 状态迁移：递增占用计数并刷新 free/busy 状态。
        slot.increaseBookedCountAndRefreshStatus(item.getAllowOverbook());
        // 持久化：保存占用后状态。
        try {
            slotRepository.save(slot);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.SLOT_CONFLICT, "号源已被占用，请刷新后重试");
        }

        // 发布事件：占用落库成功后再通知下游。
        domainEventPublisher.publish(new SlotBookedEvent(slot.getId(), slot.getId(), slot.getBookedCount(), slot.getStatusCode(), command.getOperatorId()));
        // 超售标记由 false→true 时追加发布超售事件。
        if (!Boolean.TRUE.equals(oldOverbooked) && Boolean.TRUE.equals(slot.getOverbookedFlag())) {
            domainEventPublisher.publish(new SlotOverbookedFlagSetEvent(slot.getId(), slot.getId(), slot.getOverbookedFlag(), command.getOperatorId()));
        }
    }

    /**
     * 释放预约占用：递减 bookedCount 并刷新 free/busy 状态。
     *
     * @param command 含 slotId、释放笔数（默认 1）与操作人
     * @throws BusinessException 批次条数不为 1、slotId 为空或释放笔数非法时
     */
    public void releaseBooking(ReleaseBookingCommand command) {
        // 前置校验：操作人必填。
        this.assertOperatorIdPresent(command.getOperatorId());
        ReleaseBookingCommand.ReleaseBookingItemCommand item = this.resolveSingleItem(command.resolveItems(), "一次仅允许释放一个时段的占用");
        // 前置校验：slotId 不能为空，否则无法定位聚合根。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }

        // 释放笔数未传则默认释放 1 笔占用。
        Integer releaseCount = item.getReleaseCount();
        if (releaseCount == null) {
            releaseCount = 1;
        }
        // 加载聚合：按主键加载未软删时段。
        Slot slot = this.mustGetSlot(item.getSlotId());
        // 状态迁移：递减占用计数并刷新 free/busy 状态。
        slot.decreaseBookedCountAndRefreshStatus(releaseCount);
        // 持久化：保存释放后状态。
        try {
            slotRepository.save(slot);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.SLOT_CONFLICT, "号源已被占用，请刷新后重试");
        }

        // 发布事件：释放落库成功后再通知下游。
        domainEventPublisher.publish(new SlotReleasedEvent(slot.getId(), slot.getId(), slot.getBookedCount(), slot.getStatusCode(), command.getOperatorId()));
    }

    /**
     * 标暂定占用：free → busy-tentative，表示时段被临时锁定待确认。
     *
     * @param command 含 slotId 与操作人
     * @throws BusinessException 批次条数不为 1、slotId 为空、停诊冻结或状态不允许时
     */
    public void markTentative(MarkTentativeCommand command) {
        // 前置校验：操作人必填。
        this.assertOperatorIdPresent(command.getOperatorId());
        MarkTentativeCommand.MarkTentativeItemCommand item = this.resolveSingleItem(command.resolveItems(), "一次仅允许标暂定一个时段");
        // 前置校验：slotId 不能为空，否则无法定位聚合根。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }

        // 加载聚合：按主键加载未软删时段。
        Slot slot = this.mustGetSlot(item.getSlotId());
        // 前置校验：停诊冻结时段禁止新增占用。
        stopClinicSlotFreezeDomainService.assertSlotNotFrozenForNewBooking(item.getSlotId());
        // 状态迁移：free → busy-tentative（暂定锁定）。
        slot.freeToBusyTentative();
        // 持久化：保存暂定状态。
        slotRepository.save(slot);

        // 发布事件：标暂定落库成功后再通知下游。
        domainEventPublisher.publish(new SlotTentativeMarkedEvent(slot.getId(), slot.getId(), slot.getScheduleId(), command.getOperatorId()));
    }

    /**
     * 确认暂定占用：busy-tentative → busy 并记一笔正式占用，必要时追加超售事件。
     *
     * @param command 含 slotId、是否允许超售与操作人
     * @throws BusinessException 批次条数不为 1、slotId 为空、停诊冻结或状态不允许时
     */
    public void confirmTentative(ConfirmTentativeCommand command) {
        // 前置校验：操作人必填。
        this.assertOperatorIdPresent(command.getOperatorId());
        ConfirmTentativeCommand.ConfirmTentativeItemCommand item = this.resolveSingleItem(command.resolveItems(), "一次仅允许确认一个暂定时段");
        // 前置校验：slotId 不能为空，否则无法定位聚合根。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }

        // 加载聚合：按主键加载未软删时段。
        Slot slot = this.mustGetSlot(item.getSlotId());
        // 前置校验：停诊冻结时段禁止新增占用。
        stopClinicSlotFreezeDomainService.assertSlotNotFrozenForNewBooking(item.getSlotId());
        Boolean oldOverbooked = slot.getOverbookedFlag();
        // 状态迁移：busy-tentative → busy 并递增占用计数。
        slot.busyTentativeToBusy(item.getAllowOverbook());
        // 持久化：保存确认后状态。
        slotRepository.save(slot);

        // 发布事件：确认暂定落库成功后再通知下游。
        domainEventPublisher.publish(new SlotTentativeConfirmedEvent(slot.getId(), slot.getId(), slot.getBookedCount(), slot.getStatusCode(), command.getOperatorId()));
        // 超售标记由 false→true 时追加发布超售事件。
        if (!Boolean.TRUE.equals(oldOverbooked) && Boolean.TRUE.equals(slot.getOverbookedFlag())) {
            domainEventPublisher.publish(new SlotOverbookedFlagSetEvent(slot.getId(), slot.getId(), slot.getOverbookedFlag(), command.getOperatorId()));
        }
    }

    /**
     * 标录入差错：将时段迁移为 entered-in-error 并发布差错事件。
     *
     * @param command 含 slotId、差错原因、是否强制与操作人
     * @throws BusinessException 批次条数不为 1、slotId 为空或状态不允许时
     */
    public void markEnteredInError(MarkEnteredInErrorCommand command) {
        // 前置校验：操作人必填。
        this.assertOperatorIdPresent(command.getOperatorId());
        MarkEnteredInErrorCommand.MarkEnteredInErrorItemCommand item = this.resolveSingleItem(command.resolveItems(), "一次仅允许标差错一个时段");
        // 前置校验：slotId 不能为空，否则无法定位聚合根。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }

        // 加载聚合：按主键加载未软删时段。
        Slot slot = this.mustGetSlot(item.getSlotId());
        // 状态迁移：→ entered-in-error（差错标记，后续禁止常规写操作）。
        slot.toEnteredInError(item.getReason(), item.getForce());
        // 持久化：保存差错状态。
        slotRepository.save(slot);

        // 发布事件：标差错落库成功后再通知下游。
        domainEventPublisher.publish(new SlotEnteredInErrorEvent(slot.getId(), slot.getId(), item.getReason(), command.getOperatorId()));
    }

    /**
     * 同步外部标识行：以入参为准全量对齐 SlotIdentifier 子表并发布同步事件。
     *
     * @param command 含 slotId、期望标识列表与操作人
     * @throws BusinessException 批次条数不为 1、slotId 为空、时段为差错态或标识重复/缺失时
     */
    public void syncSlotIdentifiers(SyncSlotIdentifiersCommand command) {
        // 前置校验：操作人必填。
        this.assertOperatorIdPresent(command.getOperatorId());
        SyncSlotIdentifiersCommand.SyncSlotIdentifiersItemCommand item = this.resolveSingleItem(command.resolveItems(), "一次仅允许同步一个时段标识");
        // 前置校验：slotId 不能为空，否则无法定位聚合根。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }
        // 加载聚合：按主键加载未软删时段。
        Slot slot = this.mustGetSlot(item.getSlotId());
        // 前置校验：entered-in-error 状态禁止标识同步。
        slot.assertNotEnteredInError();

        List<SyncSlotIdentifiersCommand.SlotIdentifierItemCommand> identifiers = item.getIdentifiers();
        // 入参标识列表为 null 时视为空列表（全量软删存量标识）。
        if (identifiers == null) {
            identifiers = List.of();
        }

        // 前置校验：入参内存去重，避免同一批次出现重复 (system,value)。
        Set<String> batchUniqueKeys = new HashSet<>();
        for (SyncSlotIdentifiersCommand.SlotIdentifierItemCommand identifier : identifiers) {
            // 前置校验：idSystem 不能为空。
            if (identifier.getIdSystem() == null || identifier.getIdSystem().isBlank()) {
                throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "idSystem 不能为空");
            }
            // 前置校验：idValue 不能为空。
            if (identifier.getIdValue() == null || identifier.getIdValue().isBlank()) {
                throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "idValue 不能为空");
            }
            String key = identifier.getIdSystem() + "\u0001" + identifier.getIdValue();
            // 前置校验：批次内 (system,value) 不可重复。
            if (batchUniqueKeys.contains(key)) {
                throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "存在重复的 identifier(system,value)");
            }
            batchUniqueKeys.add(key);
        }

        // Pre：查询存量标识行并按 (system,value) 建索引，做全量对齐。
        List<SlotIdentifier> existingRows = slotIdentifierRepository.findBySlotIdOrderByIdAsc(slot.getId());
        Map<String, SlotIdentifier> existingMap = new HashMap<>();
        for (SlotIdentifier row : existingRows) {
            String key = row.getIdSystem() + "\u0001" + row.getIdValue();
            existingMap.put(key, row);
        }

        // 组装：存在则更新 useCode，不存在则新增，入参未覆盖则软删。
        List<SlotIdentifier> toSave = new ArrayList<>();
        Set<String> desiredKeys = new HashSet<>();
        for (SyncSlotIdentifiersCommand.SlotIdentifierItemCommand desired : identifiers) {
            String key = desired.getIdSystem() + "\u0001" + desired.getIdValue();
            desiredKeys.add(key);
            SlotIdentifier existing = existingMap.get(key);
            // 存量标识已存在则更新 useCode。
            if (existing != null) {
                existing.setUseCode(desired.getUseCode());
                toSave.add(existing);
                continue;
            }
            SlotIdentifier created = new SlotIdentifier();
            created.setSlotId(slot.getId());
            created.setIdSystem(desired.getIdSystem());
            created.setIdValue(desired.getIdValue());
            created.setUseCode(desired.getUseCode());
            toSave.add(created);
        }
        for (SlotIdentifier existing : existingRows) {
            String key = existing.getIdSystem() + "\u0001" + existing.getIdValue();
            // 入参未覆盖的存量标识行软删。
            if (!desiredKeys.contains(key)) {
                existing.setIsDeleted(true);
                toSave.add(existing);
            }
        }

        // 持久化：批量写入对齐后的标识子表。
        if (!toSave.isEmpty()) {
            slotIdentifierRepository.saveAll(toSave);
        }

        // 发布事件：标识同步落库成功后再通知下游。
        domainEventPublisher.publish(new SlotIdentifiersSyncedEvent(slot.getId(), slot.getId(), identifiers.size(), command.getOperatorId()));
    }

    /**
     * 更新覆盖服务快照字段（类目/类型/专科/预约类型 JSON）并发布更新事件。
     *
     * @param command 含 slotId、四项服务快照 JSON 与操作人
     * @throws BusinessException 批次条数不为 1、slotId 为空或时段不存在时
     */
    public void updateOverrideServiceSnapshots(UpdateOverrideServiceSnapshotsCommand command) {
        // 前置校验：操作人必填。
        this.assertOperatorIdPresent(command.getOperatorId());
        UpdateOverrideServiceSnapshotsCommand.UpdateOverrideServiceSnapshotsItemCommand item = this.resolveSingleItem(
            command.resolveItems(), "一次仅允许更新一个时段的覆盖快照");
        // 前置校验：slotId 不能为空，否则无法定位聚合根。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }

        // 加载聚合：按主键加载未软删时段。
        Slot slot = this.mustGetSlot(item.getSlotId());
        // 状态迁移：更新服务覆盖快照（不改变 free/busy 状态）。
        slot.updateOverrideServiceSnapshots(item.getServiceCategoryJson(), item.getServiceTypeJson(), item.getSpecialtyJson(), item.getAppointmentTypeJson());
        // 持久化：保存快照字段。
        slotRepository.save(slot);

        // 发布事件：快照更新落库成功后再通知下游。
        domainEventPublisher.publish(new SlotServiceOverrideUpdatedEvent(slot.getId(), slot.getId(), command.getOperatorId()));
    }

    /**
     * 设置超售标记（手工覆盖）并发布超售事件。
     *
     * @param command 含 slotId、超售标记与操作人
     * @throws BusinessException 批次条数不为 1、slotId 为空或时段不存在时
     */
    public void setOverbooked(SetOverbookedCommand command) {
        // 前置校验：操作人必填。
        this.assertOperatorIdPresent(command.getOperatorId());
        SetOverbookedCommand.SetOverbookedItemCommand item = this.resolveSingleItem(command.resolveItems(), "一次仅允许设置一个时段的超售标记");
        // 前置校验：slotId 不能为空，否则无法定位聚合根。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }

        // 加载聚合：按主键加载未软删时段。
        Slot slot = this.mustGetSlot(item.getSlotId());
        // 状态迁移：手工设置超售标记。
        slot.setOverbookedFlag(item.getOverbooked());
        // 持久化：保存超售标记。
        slotRepository.save(slot);

        // 发布事件：超售标记落库成功后再通知下游。
        domainEventPublisher.publish(new SlotOverbookedFlagSetEvent(slot.getId(), slot.getId(), slot.getOverbookedFlag(), command.getOperatorId()));
    }

    /**
     * 更新时段备注文本，不发布领域事件。
     *
     * @param command 含 slotId、备注文本与操作人
     * @throws BusinessException 批次条数不为 1、slotId 为空或时段不存在时
     */
    public void updateSlotComment(UpdateSlotCommentCommand command) {
        // 前置校验：操作人必填。
        this.assertOperatorIdPresent(command.getOperatorId());
        UpdateSlotCommentCommand.UpdateSlotCommentItemCommand item = this.resolveSingleItem(command.resolveItems(), "一次仅允许更新一个时段备注");
        // 前置校验：slotId 不能为空，否则无法定位聚合根。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }

        // 加载聚合：按主键加载未软删时段。
        Slot slot = this.mustGetSlot(item.getSlotId());
        // 状态迁移：更新备注字段（不改变 free/busy 状态）。
        slot.updateComment(item.getCommentText());
        // 持久化：保存备注。
        slotRepository.save(slot);
    }

    /**
     * 必须加载聚合根（仅未软删）。
     *
     * <p>按主键加载号源时段，不存在则抛出业务异常。</p>
     *
     * @param slotId 时段标识
     * @return 时段实体
     * @throws BusinessException 时段不存在时
     */
    private Slot mustGetSlot(Long slotId) {
        return slotRepository.findById(slotId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SLOT_NOT_FOUND, "Slot 不存在"));
    }

    /**
     * 解析单条输入（本聚合写侧方法均按“单次操作”语义执行）。
     *
     * @param items   命令项列表
     * @param message 异常消息（当列表大小不为 1 时）
     * @return 单一命令项
     * @throws BusinessException 列表为空或大小不为 1 时
     */
    private <T> T resolveSingleItem(List<T> items, String message) {
        // 前置校验：命令批次须恰好一条（单聚合写入口）。
        if (items == null || items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, message);
        }
        return items.get(0);
    }

    /**
     * 批量出号的入参窗口合法性与“批内不重叠”校验。
     *
     * <p>校验规则：</p>
     * <ul>
     *   <li>每个模板必须包含有效的 startTime 和 endTime</li>
     *   <li>endTime 必须大于 startTime</li>
     *   <li>模板之间不能有时间重叠</li>
     * </ul>
     *
     * @param templates 批量出号模板列表
     * @throws BusinessException 时间窗口非法或存在重叠时
     */
    private void assertBatchTemplatesWindowValidAndNonOverlap(List<ProvisionSlotBatchCommand.SlotTemplateItemCommand> templates) {
        // Pre：按 startTime 排序，便于相邻重叠校验。
        List<ProvisionSlotBatchCommand.SlotTemplateItemCommand> sorted = new ArrayList<>(templates);
        sorted.sort((a, b) -> a.getStartTime().compareTo(b.getStartTime()));
        ProvisionSlotBatchCommand.SlotTemplateItemCommand prev = null;
        // Loop：逐条校验时间窗口合法性和相邻重叠。
        for (ProvisionSlotBatchCommand.SlotTemplateItemCommand cur : sorted) {
            // 前置校验：startTime/endTime 均不能为空。
            if (cur.getStartTime() == null || cur.getEndTime() == null) {
                throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotTemplates.startTime/endTime 不能为空");
            }
            // 前置校验：结束时刻须严格晚于开始时刻。
            if (!cur.getEndTime().isAfter(cur.getStartTime())) {
                throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotTemplates.endTime 必须大于 startTime");
            }
            // 前置校验：相邻模板时间窗不可重叠。
            if (prev != null) {
                if (prev.getEndTime().isAfter(cur.getStartTime())) {
                    throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotTemplates 存在重叠时间片，禁止出号");
                }
            }
            prev = cur;
        }
    }

    /**
     * 两个时间窗是否相交的判定工具。
     *
     * <p>相交判定：aStart &lt; bEnd 且 bStart &lt; aEnd。</p>
     *
     * @param aStart 时间窗 A 开始时间
     * @param aEnd   时间窗 A 结束时间
     * @param bStart 时间窗 B 开始时间
     * @param bEnd   时间窗 B 结束时间
     * @return 是否相交
     */
    private boolean isTimeWindowIntersect(LocalDateTime aStart, LocalDateTime aEnd, LocalDateTime bStart, LocalDateTime bEnd) {
        // 任一时间为 null 或窗口非法则视为不相交。
        if (aStart == null || aEnd == null || bStart == null || bEnd == null) {
            return false;
        }
        // 窗口起止不合法则视为不相交。
        if (!aEnd.isAfter(aStart) || !bEnd.isAfter(bStart)) {
            return false;
        }
        // 相交判定：a 开始早于 b 结束，且 b 开始早于 a 结束。
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    /**
     * 构建批量出号时的标识子表行。
     *
     * <p>遍历标识列表，校验必填字段，执行内存去重，构建待保存实体列表。</p>
     *
     * @param slotId      时段主键
     * @param identifiers 标识命令列表
     * @return 待保存的标识实体列表
     * @throws BusinessException idSystem/idValue 为空或重复时
     */
    private List<SlotIdentifier> buildIdentifierRows(Long slotId, List<ProvisionSlotBatchCommand.SlotIdentifierItemCommand> identifiers) {
        // 空列表直接返回，无需写子表。
        if (identifiers == null || identifiers.isEmpty()) {
            return List.of();
        }
        // Pre：入参内存去重，避免同一时段下出现重复 (system,value)。
        Set<String> uniqueKeys = new HashSet<>();
        List<SlotIdentifier> rows = new ArrayList<>();
        // Loop：遍历标识列表构建实体。
        for (ProvisionSlotBatchCommand.SlotIdentifierItemCommand identifier : identifiers) {
            // 前置校验：idSystem 不能为空。
            if (identifier.getIdSystem() == null || identifier.getIdSystem().isBlank()) {
                throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "identifier.idSystem 不能为空");
            }
            // 前置校验：idValue 不能为空。
            if (identifier.getIdValue() == null || identifier.getIdValue().isBlank()) {
                throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "identifier.idValue 不能为空");
            }
            // 使用特殊分隔符构建唯一键。
            String key = identifier.getIdSystem() + "\u0001" + identifier.getIdValue();
            // 前置校验：同一时段下 (system,value) 不可重复。
            if (uniqueKeys.contains(key)) {
                throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "同一时段下 identifier(system,value) 重复");
            }
            uniqueKeys.add(key);

            // Asm：构建标识实体。
            SlotIdentifier row = new SlotIdentifier();
            row.setSlotId(slotId);
            row.setIdSystem(identifier.getIdSystem());
            row.setIdValue(identifier.getIdValue());
            row.setUseCode(identifier.getUseCode());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 将已落库的 Slot 按 (startTime,endTime) 建索引，便于将模板上的 identifier 回填到对应 slotId。
     *
     * @param savedSlots 已保存的时段列表
     * @return 以时间窗为键的时段映射表
     */
    private Map<String, Slot> buildTemplateKeyToSavedSlot(List<Slot> savedSlots) {
        Map<String, Slot> map = new HashMap<>();
        // Loop：遍历已保存时段构建索引。
        for (Slot saved : savedSlots) {
            String key = this.buildTemplateKey(saved.getStartTime(), saved.getEndTime());
            map.put(key, saved);
        }
        return map;
    }

    /**
     * 构建批量出号模板的业务键（startTime + endTime）。
     *
     * <p>分隔符使用 {@code \u0001}，避免与业务值中常见字符产生碰撞。</p>
     *
     * @param startTime 模板开始时刻
     * @param endTime   模板结束时刻
     * @return 时间窗业务键
     */
    private String buildTemplateKey(LocalDateTime startTime, LocalDateTime endTime) {
        return startTime + "\u0001" + endTime;
    }

    /**
     * 操作人必填校验（用于审计与职责可追溯）。
     *
     * @param operatorId 操作人标识
     * @throws BusinessException operatorId 为空时
     */
    private void assertOperatorIdPresent(Long operatorId) {
        // 前置校验：operatorId 不能为空，否则无法审计写操作。
        if (operatorId == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "operatorId 不能为空");
        }
    }
}
