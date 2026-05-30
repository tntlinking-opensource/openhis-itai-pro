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

package com.openhis.health.domain.workflow.slot.service;

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
import com.openhis.health.domain.workflow.slot.command.AllocateBookingCommand;
import com.openhis.health.domain.workflow.slot.command.BlockSlotCommand;
import com.openhis.health.domain.workflow.slot.command.ConfirmTentativeCommand;
import com.openhis.health.domain.workflow.slot.command.MarkEnteredInErrorCommand;
import com.openhis.health.domain.workflow.slot.command.MarkTentativeCommand;
import com.openhis.health.domain.workflow.slot.command.ProvisionSlotBatchCommand;
import com.openhis.health.domain.workflow.slot.command.ReleaseBookingCommand;
import com.openhis.health.domain.workflow.slot.command.SetOverbookedCommand;
import com.openhis.health.domain.workflow.slot.command.SyncSlotIdentifiersCommand;
import com.openhis.health.domain.workflow.slot.command.UnblockSlotCommand;
import com.openhis.health.domain.workflow.slot.command.UpdateOverrideServiceSnapshotsCommand;
import com.openhis.health.domain.workflow.slot.command.UpdateSlotCommentCommand;
import com.openhis.health.domain.workflow.slot.entity.Slot;
import com.openhis.health.domain.workflow.slot.entity.SlotIdentifier;
import com.openhis.health.domain.workflow.slot.repository.SlotIdentifierRepository;
import com.openhis.health.domain.workflow.slot.repository.SlotRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
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
 */
@Service
@RequiredArgsConstructor
public class SlotDomainService {

    private final SlotRepository slotRepository;
    private final SlotIdentifierRepository slotIdentifierRepository;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 批量生成号源时段。
     *
     * @param command 命令
     * @return 新建时段标识列表
     */
    public List<Long> provisionSlotBatch(ProvisionSlotBatchCommand command) {
        this.assertOperatorIdPresent(command.getOperatorId());
        List<ProvisionSlotBatchCommand.ProvisionSlotBatchItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "一次仅允许批量生成一条日程的时段");
        }
        ProvisionSlotBatchCommand.ProvisionSlotBatchItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "scheduleId 不能为空");
        }
        // 业务分支处理。
        if (item.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        // 业务分支处理。
        if (item.getSlotTemplates() == null || item.getSlotTemplates().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotTemplates 不能为空");
        }

        // 先对本批入参做时间窗口校验与自相交校验，避免依赖 DB 约束才报错。
        List<ProvisionSlotBatchCommand.SlotTemplateItemCommand> templates = item.getSlotTemplates();
        this.assertBatchTemplatesWindowValidAndNonOverlap(templates);

        // 以整个批次的最小/最大时间窗一次性查询潜在相交存量，避免循环 I/O。
        LocalDateTime minStart = templates.get(0).getStartTime();
        LocalDateTime maxEnd = templates.get(0).getEndTime();
        for (ProvisionSlotBatchCommand.SlotTemplateItemCommand template : templates) {
            // 业务分支处理。
            if (template.getStartTime().isBefore(minStart)) {
                minStart = template.getStartTime();
            }
            // 业务分支处理。
            if (template.getEndTime().isAfter(maxEnd)) {
                maxEnd = template.getEndTime();
            }
        }
        List<Slot> existingIntersectSlots = slotRepository.findByScheduleIdAndStartTimeLessThanAndEndTimeGreaterThan(
            item.getScheduleId(), maxEnd, minStart);

        // 以排序+单向扫描做“模板 vs 存量”的重叠校验，避免 O(n*m) 的嵌套循环退化。
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
                // 业务分支处理。
                if (this.isTimeWindowIntersect(template.getStartTime(), template.getEndTime(), existing.getStartTime(), existing.getEndTime())) {
                    throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "存在与存量时段重叠的时间片，禁止出号");
                }
                probe++;
            }
        }

        // 组装待插入聚合根与子表行。
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

        // 先写主表再写子表，确保子表外键引用的 slotId 已生成。
        List<Slot> savedSlots = slotRepository.saveAll(slotRows);
        Map<String, Slot> templateKeyToSavedSlot = this.buildTemplateKeyToSavedSlot(savedSlots);

        for (ProvisionSlotBatchCommand.SlotTemplateItemCommand template : templates) {
            // 业务分支处理。
            if (template.getIdentifiers() == null || template.getIdentifiers().isEmpty()) {
                continue;
            }
            Slot savedSlot = templateKeyToSavedSlot.get(this.buildTemplateKey(template.getStartTime(), template.getEndTime()));
            // 业务分支处理。
            if (savedSlot == null) {
                throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "批量出号内部映射失败");
            }
            identifierRows.addAll(this.buildIdentifierRows(savedSlot.getId(), template.getIdentifiers()));
        }
        // 业务分支处理。
        if (!identifierRows.isEmpty()) {
            slotIdentifierRepository.saveAll(identifierRows);
        }

        // 事件发布必须在落库成功之后。
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
     * 封锁号源时段。
     *
     * @param command 命令
     */
    public void blockSlot(BlockSlotCommand command) {
        this.assertOperatorIdPresent(command.getOperatorId());
        BlockSlotCommand.BlockSlotItemCommand item = this.resolveSingleItem(command.resolveItems(), "一次仅允许封锁一个时段");
        // 业务分支处理。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }

        Slot slot = this.mustGetSlot(item.getSlotId());
        slot.toBusyUnavailable(item.getReason());
        slotRepository.save(slot);

        domainEventPublisher.publish(new SlotBlockedEvent(
            slot.getId(),
            slot.getId(),
            slot.getScheduleId(),
            slot.getStartTime(),
            slot.getEndTime(), command.getOperatorId()));
    }

    /**
     * 解封号源时段。
     *
     * @param command 命令
     */
    public void unblockSlot(UnblockSlotCommand command) {
        this.assertOperatorIdPresent(command.getOperatorId());
        UnblockSlotCommand.UnblockSlotItemCommand item = this.resolveSingleItem(command.resolveItems(), "一次仅允许解封一个时段");
        // 业务分支处理。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }

        Slot slot = this.mustGetSlot(item.getSlotId());
        slot.busyUnavailableToFreeOrBusy();
        slotRepository.save(slot);

        domainEventPublisher.publish(new SlotUnblockedEvent(
            slot.getId(),
            slot.getId(),
            slot.getScheduleId(),
            slot.getStatusCode(), command.getOperatorId()));
    }

    /**
     * 记预约占用。
     *
     * @param command 命令
     */
    public void allocateBooking(AllocateBookingCommand command) {
        this.assertOperatorIdPresent(command.getOperatorId());
        AllocateBookingCommand.AllocateBookingItemCommand item = this.resolveSingleItem(command.resolveItems(), "一次仅允许占用一个时段");
        // 业务分支处理。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }

        Slot slot = this.mustGetSlot(item.getSlotId());
        // 业务分支处理。
        if (Boolean.TRUE.equals(item.getTentative())) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "allocateBooking 不支持 tentative=true，请先 markTentative 再 confirmTentative");
        }

        Boolean oldOverbooked = slot.getOverbookedFlag();
        slot.increaseBookedCountAndRefreshStatus(item.getAllowOverbook());
        slotRepository.save(slot);

        domainEventPublisher.publish(new SlotBookedEvent(slot.getId(), slot.getId(), slot.getBookedCount(), slot.getStatusCode(), command.getOperatorId()));
        // 业务分支处理。
        if (!Boolean.TRUE.equals(oldOverbooked) && Boolean.TRUE.equals(slot.getOverbookedFlag())) {
            domainEventPublisher.publish(new SlotOverbookedFlagSetEvent(slot.getId(), slot.getId(), slot.getOverbookedFlag(), command.getOperatorId()));
        }
    }

    /**
     * 释放预约占用。
     *
     * @param command 命令
     */
    public void releaseBooking(ReleaseBookingCommand command) {
        this.assertOperatorIdPresent(command.getOperatorId());
        ReleaseBookingCommand.ReleaseBookingItemCommand item = this.resolveSingleItem(command.resolveItems(), "一次仅允许释放一个时段的占用");
        // 业务分支处理。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }

        Integer releaseCount = item.getReleaseCount();
        // 业务分支处理。
        if (releaseCount == null) {
            releaseCount = 1;
        }
        Slot slot = this.mustGetSlot(item.getSlotId());
        slot.decreaseBookedCountAndRefreshStatus(releaseCount);
        slotRepository.save(slot);

        domainEventPublisher.publish(new SlotReleasedEvent(slot.getId(), slot.getId(), slot.getBookedCount(), slot.getStatusCode(), command.getOperatorId()));
    }

    /**
     * 标暂定占用。
     *
     * @param command 命令
     */
    public void markTentative(MarkTentativeCommand command) {
        this.assertOperatorIdPresent(command.getOperatorId());
        MarkTentativeCommand.MarkTentativeItemCommand item = this.resolveSingleItem(command.resolveItems(), "一次仅允许标暂定一个时段");
        // 业务分支处理。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }

        Slot slot = this.mustGetSlot(item.getSlotId());
        slot.freeToBusyTentative();
        slotRepository.save(slot);

        domainEventPublisher.publish(new SlotTentativeMarkedEvent(slot.getId(), slot.getId(), slot.getScheduleId(), command.getOperatorId()));
    }

    /**
     * 确认暂定占用（并记一笔占用）。
     *
     * @param command 命令
     */
    public void confirmTentative(ConfirmTentativeCommand command) {
        this.assertOperatorIdPresent(command.getOperatorId());
        ConfirmTentativeCommand.ConfirmTentativeItemCommand item = this.resolveSingleItem(command.resolveItems(), "一次仅允许确认一个暂定时段");
        // 业务分支处理。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }

        Slot slot = this.mustGetSlot(item.getSlotId());
        Boolean oldOverbooked = slot.getOverbookedFlag();
        slot.busyTentativeToBusy(item.getAllowOverbook());
        slotRepository.save(slot);

        domainEventPublisher.publish(new SlotTentativeConfirmedEvent(slot.getId(), slot.getId(), slot.getBookedCount(), slot.getStatusCode(), command.getOperatorId()));
        // 业务分支处理。
        if (!Boolean.TRUE.equals(oldOverbooked) && Boolean.TRUE.equals(slot.getOverbookedFlag())) {
            domainEventPublisher.publish(new SlotOverbookedFlagSetEvent(slot.getId(), slot.getId(), slot.getOverbookedFlag(), command.getOperatorId()));
        }
    }

    /**
     * 标录入差错。
     *
     * @param command 命令
     */
    public void markEnteredInError(MarkEnteredInErrorCommand command) {
        this.assertOperatorIdPresent(command.getOperatorId());
        MarkEnteredInErrorCommand.MarkEnteredInErrorItemCommand item = this.resolveSingleItem(command.resolveItems(), "一次仅允许标差错一个时段");
        // 业务分支处理。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }

        Slot slot = this.mustGetSlot(item.getSlotId());
        slot.toEnteredInError(item.getReason(), item.getForce());
        slotRepository.save(slot);

        domainEventPublisher.publish(new SlotEnteredInErrorEvent(slot.getId(), slot.getId(), item.getReason(), command.getOperatorId()));
    }

    /**
     * 同步外部标识行（以入参为准全量对齐）。
     *
     * @param command 命令
     */
    public void syncSlotIdentifiers(SyncSlotIdentifiersCommand command) {
        this.assertOperatorIdPresent(command.getOperatorId());
        SyncSlotIdentifiersCommand.SyncSlotIdentifiersItemCommand item = this.resolveSingleItem(command.resolveItems(), "一次仅允许同步一个时段标识");
        // 业务分支处理。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }
        Slot slot = this.mustGetSlot(item.getSlotId());
        slot.assertNotEnteredInError();

        List<SyncSlotIdentifiersCommand.SlotIdentifierItemCommand> identifiers = item.getIdentifiers();
        // 业务分支处理。
        if (identifiers == null) {
            identifiers = List.of();
        }

        // 入参内存去重，避免同一批次出现重复 (system,value)。
        Set<String> batchUniqueKeys = new HashSet<>();
        for (SyncSlotIdentifiersCommand.SlotIdentifierItemCommand identifier : identifiers) {
            // 业务分支处理。
            if (identifier.getIdSystem() == null || identifier.getIdSystem().isBlank()) {
                throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "idSystem 不能为空");
            }
            // 业务分支处理。
            if (identifier.getIdValue() == null || identifier.getIdValue().isBlank()) {
                throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "idValue 不能为空");
            }
            String key = identifier.getIdSystem() + "\u0001" + identifier.getIdValue();
            // 业务分支处理。
            if (batchUniqueKeys.contains(key)) {
                throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "存在重复的 identifier(system,value)");
            }
            batchUniqueKeys.add(key);
        }

        // 查询存量标识行并按 (system,value) 建索引，做全量对齐：存在则更新 useCode，不存在则新增，多余则软删。
        List<SlotIdentifier> existingRows = slotIdentifierRepository.findBySlotIdOrderByIdAsc(slot.getId());
        Map<String, SlotIdentifier> existingMap = new HashMap<>();
        for (SlotIdentifier row : existingRows) {
            String key = row.getIdSystem() + "\u0001" + row.getIdValue();
            existingMap.put(key, row);
        }

        List<SlotIdentifier> toSave = new ArrayList<>();
        Set<String> desiredKeys = new HashSet<>();
        for (SyncSlotIdentifiersCommand.SlotIdentifierItemCommand desired : identifiers) {
            String key = desired.getIdSystem() + "\u0001" + desired.getIdValue();
            desiredKeys.add(key);
            SlotIdentifier existing = existingMap.get(key);
            // 业务分支处理。
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
            // 业务分支处理。
            if (!desiredKeys.contains(key)) {
                existing.setIsDeleted(true);
                toSave.add(existing);
            }
        }

        // 业务分支处理。
        if (!toSave.isEmpty()) {
            slotIdentifierRepository.saveAll(toSave);
        }

        domainEventPublisher.publish(new SlotIdentifiersSyncedEvent(slot.getId(), slot.getId(), identifiers.size(), command.getOperatorId()));
    }

    /**
     * 更新覆盖服务快照字段。
     *
     * @param command 命令
     */
    public void updateOverrideServiceSnapshots(UpdateOverrideServiceSnapshotsCommand command) {
        this.assertOperatorIdPresent(command.getOperatorId());
        UpdateOverrideServiceSnapshotsCommand.UpdateOverrideServiceSnapshotsItemCommand item = this.resolveSingleItem(
            command.resolveItems(), "一次仅允许更新一个时段的覆盖快照");
        // 业务分支处理。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }

        Slot slot = this.mustGetSlot(item.getSlotId());
        slot.updateOverrideServiceSnapshots(item.getServiceCategoryJson(), item.getServiceTypeJson(), item.getSpecialtyJson(), item.getAppointmentTypeJson());
        slotRepository.save(slot);

        domainEventPublisher.publish(new SlotServiceOverrideUpdatedEvent(slot.getId(), slot.getId(), command.getOperatorId()));
    }

    /**
     * 设置超售标记（手工覆盖）。
     *
     * @param command 命令
     */
    public void setOverbooked(SetOverbookedCommand command) {
        this.assertOperatorIdPresent(command.getOperatorId());
        SetOverbookedCommand.SetOverbookedItemCommand item = this.resolveSingleItem(command.resolveItems(), "一次仅允许设置一个时段的超售标记");
        // 业务分支处理。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }

        Slot slot = this.mustGetSlot(item.getSlotId());
        slot.setOverbookedFlag(item.getOverbooked());
        slotRepository.save(slot);

        domainEventPublisher.publish(new SlotOverbookedFlagSetEvent(slot.getId(), slot.getId(), slot.getOverbookedFlag(), command.getOperatorId()));
    }

    /**
     * 更新备注。
     *
     * @param command 命令
     */
    public void updateSlotComment(UpdateSlotCommentCommand command) {
        this.assertOperatorIdPresent(command.getOperatorId());
        UpdateSlotCommentCommand.UpdateSlotCommentItemCommand item = this.resolveSingleItem(command.resolveItems(), "一次仅允许更新一个时段备注");
        // 业务分支处理。
        if (item.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 不能为空");
        }

        Slot slot = this.mustGetSlot(item.getSlotId());
        slot.updateComment(item.getCommentText());
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
            if (cur.getStartTime() == null || cur.getEndTime() == null) {
                throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotTemplates.startTime/endTime 不能为空");
            }
            if (!cur.getEndTime().isAfter(cur.getStartTime())) {
                throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotTemplates.endTime 必须大于 startTime");
            }
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
        if (aStart == null || aEnd == null || bStart == null || bEnd == null) {
            return false;
        }
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
        // 业务分支处理：空列表直接返回。
        if (identifiers == null || identifiers.isEmpty()) {
            return List.of();
        }
        // Pre：入参内存去重，避免同一时段下出现重复 (system,value)。
        Set<String> uniqueKeys = new HashSet<>();
        List<SlotIdentifier> rows = new ArrayList<>();
        // Loop：遍历标识列表构建实体。
        for (ProvisionSlotBatchCommand.SlotIdentifierItemCommand identifier : identifiers) {
            if (identifier.getIdSystem() == null || identifier.getIdSystem().isBlank()) {
                throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "identifier.idSystem 不能为空");
            }
            if (identifier.getIdValue() == null || identifier.getIdValue().isBlank()) {
                throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "identifier.idValue 不能为空");
            }
            // 使用特殊分隔符构建唯一键。
            String key = identifier.getIdSystem() + "\u0001" + identifier.getIdValue();
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
     */
    private String buildTemplateKey(LocalDateTime startTime, LocalDateTime endTime) {
        return startTime + "\u0001" + endTime;
    }

    /**
     * 操作人必填校验（用于审计与职责可追溯）。
     */
    private void assertOperatorIdPresent(Long operatorId) {
        // 业务分支处理。
        if (operatorId == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "operatorId 不能为空");
        }
    }
}
