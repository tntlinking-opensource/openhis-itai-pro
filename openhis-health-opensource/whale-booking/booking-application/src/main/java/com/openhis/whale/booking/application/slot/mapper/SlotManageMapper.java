package com.openhis.whale.booking.application.slot.mapper;

import com.openhis.health.domain.share.enums.ScheduleActorKind;
import com.openhis.health.domain.share.enums.SlotStatus;
import com.openhis.whale.booking.application.slot.request.PageSlotsByDateRequest;
import com.openhis.whale.booking.application.slot.request.SetOverbookedRequest;
import com.openhis.whale.booking.application.slot.response.SlotDetailDto;
import com.openhis.whale.booking.application.slot.response.SlotListItemDto;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleActorDateModel;
import com.openhis.whale.booking.domain.slot.command.BlockSlotCommand;
import com.openhis.whale.booking.domain.slot.command.SetOverbookedCommand;
import com.openhis.whale.booking.domain.slot.command.UnblockSlotCommand;
import com.openhis.whale.booking.domain.slot.queries.PageSlotsByOrganizationAndDateQuery;
import com.openhis.whale.booking.domain.slot.queries.model.SlotDetailModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 号源日历应用层映射器。
 */
@Component
public class SlotManageMapper {

    /**
     * HTTP 请求 → 领域分页查询。
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
     */
    public SlotListItemDto toSlotListItemDto(SlotDetailModel model) {
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
        dto.setDisplayStatus(this.resolveDisplayStatus(model));
        dto.setOverbookedFlag(model.getOverbookedFlag());
        return dto;
    }

    /**
     * 领域明细 → 详情 DTO。
     */
    public SlotDetailDto toSlotDetailDto(SlotDetailModel model) {
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
        dto.setDisplayStatus(this.resolveDisplayStatus(model));
        dto.setOverbookedFlag(model.getOverbookedFlag());
        dto.setCommentText(model.getCommentText());
        return dto;
    }

    /**
     * 封锁请求 → 单条领域命令。
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
     */
    public UnblockSlotCommand toUnblockSlotCommand(Long slotId, Long operatorId) {
        UnblockSlotCommand command = new UnblockSlotCommand();
        command.setOperatorId(operatorId);
        UnblockSlotCommand.UnblockSlotItemCommand item = new UnblockSlotCommand.UnblockSlotItemCommand();
        item.setSlotId(slotId);
        command.setItem(item);
        return command;
    }

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

    private String resolveDisplayStatus(SlotDetailModel model) {
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
