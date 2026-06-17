package com.openhis.whale.booking.application.slot;

import com.openhis.whale.booking.application.slot.mapper.SlotManageMapper;
import com.openhis.whale.booking.application.slot.request.BlockSlotsRequest;
import com.openhis.whale.booking.application.slot.request.LoadSlotDetailRequest;
import com.openhis.whale.booking.application.slot.request.PageSlotsByDateRequest;
import com.openhis.whale.booking.application.slot.request.SetOverbookedRequest;
import com.openhis.whale.booking.application.slot.request.UnblockSlotsRequest;
import com.openhis.whale.booking.application.slot.response.PageSlotsByDateResponse;
import com.openhis.whale.booking.application.slot.response.SlotDetailDto;
import com.openhis.whale.booking.application.slot.response.SlotListItemDto;
import com.openhis.whale.booking.application.support.BookingOperatorResolver;
import com.openhis.whale.booking.application.support.BookingPagedResultBuilder;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleActorDateModel;
import com.openhis.whale.booking.domain.schedule.service.ScheduleQueryService;
import com.openhis.whale.booking.domain.slot.command.BlockSlotCommand;
import com.openhis.whale.booking.domain.slot.command.SetOverbookedCommand;
import com.openhis.whale.booking.domain.slot.command.UnblockSlotCommand;
import com.openhis.whale.booking.domain.slot.queries.PageSlotsByOrganizationAndDateQuery;
import com.openhis.whale.booking.domain.slot.queries.model.SlotDetailModel;
import com.openhis.whale.booking.domain.slot.service.SlotDomainService;
import com.openhis.whale.booking.domain.slot.service.SlotQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * 号源日历应用服务。
 *
 * <p>职责边界：号源查询、封锁/解封编排；写侧委托 SlotDomainService。</p>
 * <p>接入：POST /api/app/slotManage/{methodName}</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class SlotManageAppService {

    private final SlotQueryService slotQueryService;
    private final SlotDomainService slotDomainService;
    private final ScheduleQueryService scheduleQueryService;
    private final SlotManageMapper slotManageMapper;
    private final BookingOperatorResolver bookingOperatorResolver;

    /**
     * 按机构与日期分页查询号源（3.1）。
     *
     * @param request 查询条件
     * @return 分页列表
     */
    public PageSlotsByDateResponse pageSlotsByDate(@Valid PageSlotsByDateRequest request) {
        List<Long> scheduleIds = this.resolveScheduleIdsForPractitionerFilter(request);
        PageSlotsByOrganizationAndDateQuery query = slotManageMapper.toPageSlotsQuery(request, scheduleIds);
        Page<SlotDetailModel> page = slotQueryService.pageSlotsByOrganizationAndDate(query);
        List<SlotListItemDto> rows = new ArrayList<>();
        for (SlotDetailModel model : page.getContent()) {
            rows.add(slotManageMapper.toSlotListItemDto(model));
        }
        return BookingPagedResultBuilder.build(new PageSlotsByDateResponse(), rows, page);
    }

    /**
     * 加载号源详情（3.2）。
     *
     * @param request 详情请求
     * @return 详情 DTO
     */
    public SlotDetailDto loadSlotDetail(@Valid LoadSlotDetailRequest request) {
        SlotDetailModel model = slotQueryService.loadSlotById(request.getSlotId());
        return slotManageMapper.toSlotDetailDto(model);
    }

    /**
     * 批量封锁号源（3.3）。领域单次仅处理一条，应用层循环编排。
     *
     * @param request 封锁请求
     */
    @Transactional
    public void blockSlots(@Valid BlockSlotsRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        for (Long slotId : request.getSlotIds()) {
            if (slotId == null) {
                continue;
            }
            BlockSlotCommand command = slotManageMapper.toBlockSlotCommand(slotId, request.getReason(), operatorId);
            slotDomainService.blockSlot(command);
        }
    }

    /**
     * 批量解封号源（3.3）。
     *
     * @param request 解封请求
     */
    @Transactional
    public void unblockSlots(@Valid UnblockSlotsRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        for (Long slotId : request.getSlotIds()) {
            if (slotId == null) {
                continue;
            }
            UnblockSlotCommand command = slotManageMapper.toUnblockSlotCommand(slotId, operatorId);
            slotDomainService.unblockSlot(command);
        }
    }

    /**
     * 设置超售标记（3.4）。
     */
    @Transactional
    public void setOverbooked(@Valid SetOverbookedRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        SetOverbookedCommand command = slotManageMapper.toSetOverbookedCommand(request, operatorId);
        slotDomainService.setOverbooked(command);
    }

    /**
     * 按医师参与者收窄日程主键；未选医师时不追加 IN 条件。
     */
    private List<Long> resolveScheduleIdsForPractitionerFilter(PageSlotsByDateRequest request) {
        if (request.getPractitionerId() == null) {
            return null;
        }
        List<ScheduleActorDateModel> actorSchedules = scheduleQueryService.findSchedulesByActorAndDate(
            slotManageMapper.practitionerActorType(),
            request.getPractitionerId(),
            request.getDate());
        List<Long> scheduleIds = slotManageMapper.toScheduleIdsFromActorDateModels(actorSchedules);
        if (scheduleIds.isEmpty()) {
            return new ArrayList<>();
        }
        return scheduleIds;
    }
}
