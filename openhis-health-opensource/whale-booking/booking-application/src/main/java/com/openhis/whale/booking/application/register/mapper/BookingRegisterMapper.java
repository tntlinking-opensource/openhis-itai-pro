package com.openhis.whale.booking.application.register.mapper;

import com.openhis.health.domain.share.enums.ScheduleActorKind;
import com.openhis.whale.booking.application.register.request.CancelBookingRequest;
import com.openhis.whale.booking.application.register.request.CreateOutpatientBookingRequest;
import com.openhis.whale.booking.application.register.request.PageBookingsRequest;
import com.openhis.whale.booking.application.register.request.PagePatientsForSelectRequest;
import com.openhis.whale.booking.application.register.request.RescheduleBookingRequest;
import com.openhis.whale.booking.application.register.response.BookableSlotDto;
import com.openhis.whale.booking.application.register.response.BookingDetailDto;
import com.openhis.whale.booking.application.register.response.BookingListItemDto;
import com.openhis.whale.booking.application.register.response.CreateOutpatientBookingResponse;
import com.openhis.whale.booking.application.register.response.ListBookableSlotsResponse;
import com.openhis.whale.booking.application.register.response.PagePatientsForSelectResponse;
import com.openhis.whale.booking.application.register.response.RescheduleBookingResponse;
import com.openhis.whale.booking.contracts.dto.BookingPatientPageResult;
import com.openhis.whale.booking.contracts.query.BookingPatientSearchQuery;
import com.openhis.whale.booking.domain.appointment.command.CancelAppointmentCommand;
import com.openhis.whale.booking.domain.appointment.command.CreateAppointmentCommand;
import com.openhis.whale.booking.domain.appointment.command.RescheduleOutpatientBookingCommand;
import com.openhis.whale.booking.domain.appointment.entity.Appointment;
import com.openhis.whale.booking.domain.appointment.queries.AppointmentPageQuery;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentBookingDetailModel;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentBookingListModel;
import com.openhis.whale.booking.domain.schedule.queries.CalendarScheduleQuery;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleActorModel;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleCalendarItemModel;
import com.openhis.whale.booking.domain.slot.command.AllocateBookingCommand;
import com.openhis.whale.booking.domain.slot.command.ReleaseBookingCommand;
import com.openhis.whale.booking.domain.slot.queries.model.BookableSlotModel;
import com.openhis.whale.booking.domain.slot.queries.model.SlotDetailModel;
import com.openhis.whale.booking.domain.template.queries.model.ResolvedDaySchedulePlan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 门诊预约应用层映射器。
 */
@Component
public class BookingRegisterMapper {

    private static final String OUTPATIENT_BOOKING_KIND = "OUTPATIENT_BOOKING";
    private static final String DEFAULT_ORIGIN_CHANNEL = "WINDOW";

    /**
     * 列表预约请求 → 领域分页查询。
     */
    public AppointmentPageQuery toAppointmentPageQuery(PageBookingsRequest request) {
        AppointmentPageQuery query = new AppointmentPageQuery();
        query.setOrganizationId(request.getOrganizationId());
        query.setPatientId(request.getPatientId());
        query.setDateFrom(request.getDateFrom());
        query.setDateTo(request.getDateTo());
        query.setKeyword(request.getKeyword());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        return query;
    }

    /**
     * 患者检索请求 → Port 查询。
     */
    public BookingPatientSearchQuery toBookingPatientSearchQuery(PagePatientsForSelectRequest request) {
        BookingPatientSearchQuery query = new BookingPatientSearchQuery();
        query.setKeyword(request.getKeyword());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        return query;
    }

    /**
     * Port 患者分页 → 应用响应。
     */
    public PagePatientsForSelectResponse toPagePatientsForSelectResponse(BookingPatientPageResult pageResult) {
        PagePatientsForSelectResponse response = new PagePatientsForSelectResponse();
        if (pageResult == null) {
            response.setContent(new ArrayList<>());
            response.setTotalCount(0L);
            response.setPageNo(1);
            response.setPageSize(0);
            return response;
        }
        response.setContent(pageResult.getContent());
        response.setTotalCount(pageResult.getTotalCount());
        response.setPageNo(pageResult.getPageNo());
        response.setPageSize(pageResult.getPageSize());
        return response;
    }

    /**
     * 可约号源 HTTP 请求 → 日历日程查询（用于解析 scheduleId 集合）。
     */
    public CalendarScheduleQuery toCalendarScheduleQuery(
        Long organizationId,
        java.time.LocalDate date,
        Long practitionerId,
        Long healthcareServiceId) {
        CalendarScheduleQuery query = new CalendarScheduleQuery();
        query.setOrganizationId(organizationId);
        query.setRangeStart(date);
        query.setRangeEnd(date);
        query.setPractitionerId(practitionerId);
        query.setHealthcareServiceId(healthcareServiceId);
        return query;
    }

    /**
     * 领域可约模型列表 → HTTP 响应。
     */
    public ListBookableSlotsResponse toListBookableSlotsResponse(
        List<BookableSlotModel> models,
        List<SlotDetailModel> slotDetails) {
        ListBookableSlotsResponse response = new ListBookableSlotsResponse();
        List<BookableSlotDto> slots = new ArrayList<>();
        if (models != null) {
            for (BookableSlotModel model : models) {
                SlotDetailModel detail = this.findSlotDetail(slotDetails, model.getSlotId());
                slots.add(this.toBookableSlotDto(model, detail));
            }
        }
        response.setSlots(slots);
        return response;
    }

    /**
     * 占号命令。
     */
    public AllocateBookingCommand toAllocateBookingCommand(Long slotId, Long operatorId) {
        AllocateBookingCommand command = new AllocateBookingCommand();
        command.setOperatorId(operatorId);
        AllocateBookingCommand.AllocateBookingItemCommand item = new AllocateBookingCommand.AllocateBookingItemCommand();
        item.setSlotId(slotId);
        item.setAllowOverbook(false);
        item.setTentative(false);
        command.setItem(item);
        return command;
    }

    /**
     * 释放占号命令。
     */
    public ReleaseBookingCommand toReleaseBookingCommand(Long slotId, Long operatorId) {
        ReleaseBookingCommand command = new ReleaseBookingCommand();
        command.setOperatorId(operatorId);
        ReleaseBookingCommand.ReleaseBookingItemCommand item = new ReleaseBookingCommand.ReleaseBookingItemCommand();
        item.setSlotId(slotId);
        item.setReleaseCount(1);
        command.setItem(item);
        return command;
    }

    /**
     * 创建预约命令。
     */
    public CreateAppointmentCommand toCreateAppointmentCommand(
        CreateOutpatientBookingRequest request,
        SlotDetailModel slot,
        Long destinationLocationId,
        Long operatorId) {
        CreateAppointmentCommand command = new CreateAppointmentCommand();
        command.setOperatorId(operatorId);
        CreateAppointmentCommand.CreateAppointmentItemCommand item = new CreateAppointmentCommand.CreateAppointmentItemCommand();
        item.setAppointmentKind(OUTPATIENT_BOOKING_KIND);
        item.setOrganizationId(request.getOrganizationId());
        item.setPatientId(request.getPatientId());
        item.setSlotId(request.getSlotId());
        item.setDestinationLocationId(destinationLocationId);
        item.setStartInstant(slot.getStartTime());
        item.setEndInstant(slot.getEndTime());
        CreateAppointmentCommand.AppointmentOriginCommand origin = new CreateAppointmentCommand.AppointmentOriginCommand();
        String channel = request.getOriginChannel();
        if (channel == null || channel.trim().isEmpty()) {
            channel = DEFAULT_ORIGIN_CHANNEL;
        }
        origin.setOriginChannel(channel.trim());
        origin.setOriginOperatorId(operatorId);
        item.setOrigin(origin);
        command.setItem(item);
        return command;
    }

    /**
     * 创建预约响应。
     */
    public CreateOutpatientBookingResponse toCreateOutpatientBookingResponse(Appointment appointment) {
        CreateOutpatientBookingResponse response = new CreateOutpatientBookingResponse();
        response.setAppointmentId(appointment.getId());
        response.setBusNo(appointment.getBusNo());
        return response;
    }

    /**
     * 取消预约命令。
     */
    public CancelAppointmentCommand toCancelAppointmentCommand(CancelBookingRequest request, Long operatorId) {
        CancelAppointmentCommand command = new CancelAppointmentCommand();
        command.setOperatorId(operatorId);
        CancelAppointmentCommand.CancelAppointmentItemCommand item = new CancelAppointmentCommand.CancelAppointmentItemCommand();
        item.setAppointmentId(request.getAppointmentId());
        item.setReason(request.getReason());
        item.setCancellationReasonCode(request.getCancellationReasonCode());
        command.setItem(item);
        return command;
    }

    public RescheduleOutpatientBookingCommand toRescheduleOutpatientBookingCommand(
        RescheduleBookingRequest request, SlotDetailModel newSlot, Long operatorId) {
        RescheduleOutpatientBookingCommand command = new RescheduleOutpatientBookingCommand();
        command.setOperatorId(operatorId);
        command.setAppointmentId(request.getAppointmentId());
        command.setNewSlotId(request.getNewSlotId());
        command.setNewStartInstant(newSlot.getStartTime());
        command.setNewEndInstant(newSlot.getEndTime());
        command.setDestinationLocationId(request.getDestinationLocationId());
        return command;
    }

    public RescheduleBookingResponse toRescheduleBookingResponse(Long appointmentId, Long newSlotId) {
        RescheduleBookingResponse response = new RescheduleBookingResponse();
        response.setAppointmentId(appointmentId);
        response.setNewSlotId(newSlotId);
        return response;
    }

    /**
     * 列表行映射。
     */
    public BookingListItemDto toBookingListItemDto(AppointmentBookingListModel model) {
        BookingListItemDto dto = new BookingListItemDto();
        dto.setAppointmentId(model.getId());
        dto.setBusNo(model.getBusNo());
        dto.setPatientId(model.getPatientId());
        dto.setOrganizationId(model.getOrganizationId());
        dto.setSlotId(model.getSlotId());
        dto.setStartInstant(model.getStartInstant());
        dto.setEndInstant(model.getEndInstant());
        dto.setDestinationLocationId(model.getDestinationLocationId());
        dto.setStatusView(model.getStatusView());
        return dto;
    }

    /**
     * 详情映射（合并 Slot / Schedule 参与者快照）。
     */
    public BookingDetailDto toBookingDetailDto(
        AppointmentBookingDetailModel model,
        SlotDetailModel slot,
        List<ScheduleActorModel> actors) {
        BookingDetailDto dto = new BookingDetailDto();
        dto.setAppointmentId(model.getId());
        dto.setBusNo(model.getBusNo());
        dto.setAppointmentKind(model.getAppointmentKind());
        dto.setPatientId(model.getPatientId());
        dto.setOrganizationId(model.getOrganizationId());
        dto.setSlotId(model.getSlotId());
        if (slot != null) {
            dto.setScheduleId(slot.getScheduleId());
            dto.setStartInstant(slot.getStartTime());
            dto.setEndInstant(slot.getEndTime());
        } else {
            dto.setStartInstant(model.getStartInstant());
            dto.setEndInstant(model.getEndInstant());
        }
        dto.setDestinationLocationId(model.getDestinationLocationId());
        dto.setStatusView(model.getStatusView());
        dto.setPractitionerId(this.resolveActorId(actors, ScheduleActorKind.PRACTITIONER));
        if (dto.getDestinationLocationId() == null) {
            dto.setDestinationLocationId(this.resolveActorId(actors, ScheduleActorKind.LOCATION));
        }
        return dto;
    }

    /**
     * 从日历项提取 scheduleId 列表。
     */
    public List<Long> toScheduleIds(List<ScheduleCalendarItemModel> schedules) {
        List<Long> scheduleIds = new ArrayList<>();
        if (schedules == null) {
            return scheduleIds;
        }
        for (ScheduleCalendarItemModel schedule : schedules) {
            if (schedule.getId() != null) {
                scheduleIds.add(schedule.getId());
            }
        }
        return scheduleIds;
    }

    public Integer practitionerActorType() {
        return ScheduleActorKind.PRACTITIONER.getSortOrder().intValue();
    }

    public Integer locationActorType() {
        return ScheduleActorKind.LOCATION.getSortOrder().intValue();
    }

    /**
     * 从 Schedule 参与者解析诊室主键。
     */
    public Long resolveLocationIdFromActors(List<ScheduleActorModel> actors) {
        return this.resolveActorId(actors, ScheduleActorKind.LOCATION);
    }

    private BookableSlotDto toBookableSlotDto(BookableSlotModel model, SlotDetailModel detail) {
        BookableSlotDto dto = new BookableSlotDto();
        dto.setSlotId(model.getSlotId());
        dto.setScheduleId(model.getScheduleId());
        dto.setStartTime(model.getStartTime());
        dto.setEndTime(model.getEndTime());
        if (detail != null) {
            dto.setCapacity(detail.getCapacity());
            dto.setBookedCount(detail.getBookedCount());
            dto.setRemainingCount(this.resolveRemaining(detail.getCapacity(), detail.getBookedCount()));
        }
        dto.setFullFlag(model.getIsFull());
        return dto;
    }

    public BookableSlotDto toVirtualBookableSlotDto(ResolvedDaySchedulePlan.VirtualSlotPeriod period) {
        BookableSlotDto dto = new BookableSlotDto();
        dto.setFromTemplate(true);
        dto.setVirtualSlotKey(period.getVirtualSlotKey());
        dto.setStartTime(period.getStartTime());
        dto.setEndTime(period.getEndTime());
        dto.setCapacity(period.getCapacity());
        dto.setBookedCount(0);
        dto.setRemainingCount(period.getCapacity());
        dto.setFullFlag(false);
        return dto;
    }

    private SlotDetailModel findSlotDetail(List<SlotDetailModel> details, Long slotId) {
        if (details == null || slotId == null) {
            return null;
        }
        for (SlotDetailModel detail : details) {
            if (slotId.equals(detail.getId())) {
                return detail;
            }
        }
        return null;
    }

    private Integer resolveRemaining(Integer capacity, Integer bookedCount) {
        if (capacity == null || bookedCount == null) {
            return null;
        }
        int remaining = capacity - bookedCount;
        if (remaining < 0) {
            return 0;
        }
        return remaining;
    }

    private Long resolveActorId(List<ScheduleActorModel> actors, ScheduleActorKind kind) {
        if (actors == null) {
            return null;
        }
        Integer actorType = kind.getSortOrder().intValue();
        for (ScheduleActorModel actor : actors) {
            if (actorType.equals(actor.getActorType())) {
                return actor.getActorId();
            }
        }
        return null;
    }
}
