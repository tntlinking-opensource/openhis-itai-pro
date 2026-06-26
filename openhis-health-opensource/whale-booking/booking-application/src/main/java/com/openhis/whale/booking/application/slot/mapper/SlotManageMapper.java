package com.openhis.whale.booking.application.slot.mapper;

import com.openhis.health.domain.share.enums.ScheduleActorKind;
import com.openhis.health.domain.share.enums.ScheduleStatus;
import com.openhis.health.domain.share.enums.SlotStatus;
import com.openhis.whale.booking.application.slot.request.PageSlotsByDateRequest;
import com.openhis.whale.booking.application.slot.request.SetOverbookedRequest;
import com.openhis.whale.booking.application.slot.response.SlotDetailDto;
import com.openhis.whale.booking.application.slot.response.SlotListItemDto;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleActorDateModel;
import com.openhis.whale.booking.domain.slot.command.BlockSlotCommand;
import com.openhis.whale.booking.domain.slot.command.RetireSlotCommand;
import com.openhis.whale.booking.domain.slot.command.SetOverbookedCommand;
import com.openhis.whale.booking.domain.slot.command.UnblockSlotCommand;
import com.openhis.whale.booking.domain.slot.queries.PageSlotsByOrganizationAndDateQuery;
import com.openhis.whale.booking.domain.slot.queries.model.SlotDetailModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 号源日历应用层映射器。
 * <p>职责：号源分页/详情/封锁/超售 HTTP 契约 ↔ 领域 Query/Command；展示状态在此推导。</p>
 */
@Component
public class SlotManageMapper {

    /**
     * HTTP 请求 → 领域分页查询。
     *
     * @param request 按日期分页号源请求
     * @param scheduleIds 日程主键列表
     * @return 按机构与日期分页号源查询
     */
    public PageSlotsByOrganizationAndDateQuery toPageSlotsQuery(PageSlotsByDateRequest request, List<Long> scheduleIds) {
        PageSlotsByOrganizationAndDateQuery query = new PageSlotsByOrganizationAndDateQuery();
        query.setOrganizationId(request.getOrganizationId());
        query.setDate(request.getDate());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        query.setScheduleId(request.getScheduleId());
        query.setStatusCode(request.getStatusCode());
        query.setScheduleIds(scheduleIds);
        return query;
    }

    /**
     * 从医师当日日程解析主键列表。
     *
     * @param models 日程参与者日期模型列表
     * @return 日程主键列表
     */
    public List<Long> toScheduleIdsFromActorDateModels(List<ScheduleActorDateModel> models) {
        List<Long> scheduleIds = new ArrayList<>();
        if (models == null) {
            return scheduleIds;
        }
        for (ScheduleActorDateModel model : models) {
            if (model.getScheduleId() != null) {
                scheduleIds.add(model.getScheduleId());
            }
        }
        return scheduleIds;
    }

    /**
     * 领域明细 → 列表行 DTO。
     *
     * @param model 号源明细模型
     * @return 号源列表行 DTO
     */
    public SlotListItemDto toSlotListItemDto(SlotDetailModel model) {
        return this.toSlotListItemDto(model, null);
    }

    /**
     * 领域明细 → 列表行 DTO（含所属日程状态，用于停诊展示）。
     *
     * @param model 号源明细模型
     * @param scheduleStatusEnum 所属日程 status_enum；为空时仅按号源状态推导
     * @return 号源列表行 DTO
     */
    public SlotListItemDto toSlotListItemDto(SlotDetailModel model, String scheduleStatusEnum) {
        SlotListItemDto dto = new SlotListItemDto();
        dto.setSlotId(model.getId());
        dto.setScheduleId(model.getScheduleId());
        dto.setOrganizationId(model.getOrganizationId());
        dto.setStartTime(model.getStartTime());
        dto.setEndTime(model.getEndTime());
        dto.setStatusCode(model.getStatusCode());
        dto.setCapacity(model.getCapacity());
        dto.setBookedCount(model.getBookedCount());
        dto.setRemainingCount(this.resolveRemainingCount(model.getCapacity(), model.getBookedCount()));
        dto.setDisplayStatus(this.resolveDisplayStatus(model, scheduleStatusEnum));
        dto.setOverbookedFlag(model.getOverbookedFlag());
        return dto;
    }

    /**
     * 领域明细 → 详情 DTO。
     *
     * @param model 号源明细模型
     * @return 号源详情 DTO
     */
    public SlotDetailDto toSlotDetailDto(SlotDetailModel model) {
        return this.toSlotDetailDto(model, null);
    }

    /**
     * 领域明细 → 详情 DTO（含所属日程状态，用于停诊展示）。
     *
     * @param model 号源明细模型
     * @param scheduleStatusEnum 所属日程 status_enum；为空时仅按号源状态推导
     * @return 号源详情 DTO
     */
    public SlotDetailDto toSlotDetailDto(SlotDetailModel model, String scheduleStatusEnum) {
        SlotDetailDto dto = new SlotDetailDto();
        dto.setSlotId(model.getId());
        dto.setScheduleId(model.getScheduleId());
        dto.setOrganizationId(model.getOrganizationId());
        dto.setStartTime(model.getStartTime());
        dto.setEndTime(model.getEndTime());
        dto.setStatusCode(model.getStatusCode());
        dto.setCapacity(model.getCapacity());
        dto.setBookedCount(model.getBookedCount());
        dto.setRemainingCount(this.resolveRemainingCount(model.getCapacity(), model.getBookedCount()));
        dto.setDisplayStatus(this.resolveDisplayStatus(model, scheduleStatusEnum));
        dto.setOverbookedFlag(model.getOverbookedFlag());
        dto.setCommentText(model.getCommentText());
        return dto;
    }

    /**
     * 封锁请求 → 单条领域命令。
     *
     * @param slotId 号源主键
     * @param reason 封锁原因
     * @param operatorId 操作人主键
     * @return 封锁号源命令
     */
    public BlockSlotCommand toBlockSlotCommand(Long slotId, String reason, Long operatorId) {
        BlockSlotCommand command = new BlockSlotCommand();
        command.setOperatorId(operatorId);
        BlockSlotCommand.BlockSlotItemCommand item = new BlockSlotCommand.BlockSlotItemCommand();
        item.setSlotId(slotId);
        item.setReason(reason);
        command.setItem(item);
        return command;
    }

    /**
     * 解封请求 → 单条领域命令。
     *
     * @param slotId 号源主键
     * @param operatorId 操作人主键
     * @return 解封号源命令
     */
    public UnblockSlotCommand toUnblockSlotCommand(Long slotId, Long operatorId) {
        UnblockSlotCommand command = new UnblockSlotCommand();
        command.setOperatorId(operatorId);
        UnblockSlotCommand.UnblockSlotItemCommand item = new UnblockSlotCommand.UnblockSlotItemCommand();
        item.setSlotId(slotId);
        command.setItem(item);
        return command;
    }

    /**
     * 删除请求 → 单条领域命令。
     *
     * @param slotId 号源主键
     * @param operatorId 操作人主键
     * @return 删除号源命令
     */
    public RetireSlotCommand toRetireSlotCommand(Long slotId, Long operatorId) {
        RetireSlotCommand command = new RetireSlotCommand();
        command.setOperatorId(operatorId);
        RetireSlotCommand.RetireSlotItemCommand item = new RetireSlotCommand.RetireSlotItemCommand();
        item.setSlotId(slotId);
        command.setItem(item);
        return command;
    }

    /**
     * 超售标记请求 → 领域命令。
     *
     * @param request 超售标记请求
     * @param operatorId 操作人主键
     * @return 设置超售命令
     */
    public SetOverbookedCommand toSetOverbookedCommand(SetOverbookedRequest request, Long operatorId) {
        SetOverbookedCommand command = new SetOverbookedCommand();
        command.setOperatorId(operatorId);
        SetOverbookedCommand.SetOverbookedItemCommand item = new SetOverbookedCommand.SetOverbookedItemCommand();
        item.setSlotId(request.getSlotId());
        item.setOverbooked(request.getOverbooked());
        command.setItem(item);
        return command;
    }

    /**
     * 医师参与者类型编码（与 {@link ScheduleActorKind#PRACTITIONER} 对齐）。
     *
     * @return 医师参与者类型编码
     */
    public Integer practitionerActorType() {
        return ScheduleActorKind.PRACTITIONER.getSortOrder().intValue();
    }

    private Integer resolveRemainingCount(Integer capacity, Integer bookedCount) {
        if (capacity == null || bookedCount == null) {
            return null;
        }
        int remaining = capacity - bookedCount;
        if (remaining < 0) {
            return 0;
        }
        return remaining;
    }

    private String resolveDisplayStatus(SlotDetailModel model, String scheduleStatusEnum) {
        // 所属日程已停诊/作废：即使 slot 仍为 free 也展示停诊（IF-2.9 日程 Deactivate）
        if (scheduleStatusEnum != null && !ScheduleStatus.ACTIVE.getCode().equals(scheduleStatusEnum)) {
            return "停诊";
        }
        // 号源自身封锁优先，其次按剩余容量判「已满/可约」
        if (SlotStatus.BUSY_UNAVAILABLE.getCode().equals(model.getStatusCode())) {
            return "停诊";
        }
        Integer remaining = this.resolveRemainingCount(model.getCapacity(), model.getBookedCount());
        if (remaining != null && remaining <= 0) {
            return "已满";
        }
        return "可约";
    }
}
