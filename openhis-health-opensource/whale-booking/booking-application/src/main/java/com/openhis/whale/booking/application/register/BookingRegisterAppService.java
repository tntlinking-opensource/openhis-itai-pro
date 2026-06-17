package com.openhis.whale.booking.application.register;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.application.register.mapper.BookingRegisterMapper;
import com.openhis.whale.booking.application.register.request.CancelBookingRequest;
import com.openhis.whale.booking.application.register.request.CancelWaitlistRequest;
import com.openhis.whale.booking.application.register.request.CreateOutpatientBookingRequest;
import com.openhis.whale.booking.application.register.request.JoinWaitlistRequest;
import com.openhis.whale.booking.application.register.request.ListBookableSlotsRequest;
import com.openhis.whale.booking.application.register.request.LoadBookingDetailRequest;
import com.openhis.whale.booking.application.register.request.PageBookingsRequest;
import com.openhis.whale.booking.application.register.request.PagePatientsForSelectRequest;
import com.openhis.whale.booking.application.register.request.RescheduleBookingRequest;
import com.openhis.whale.booking.application.register.response.BookingDetailDto;
import com.openhis.whale.booking.application.register.response.BookingListItemDto;
import com.openhis.whale.booking.application.register.response.CancelBookingResponse;
import com.openhis.whale.booking.application.register.response.CreateOutpatientBookingResponse;
import com.openhis.whale.booking.application.register.response.JoinWaitlistResponse;
import com.openhis.whale.booking.application.register.response.ListBookableSlotsResponse;
import com.openhis.whale.booking.application.register.response.PageBookingsResponse;
import com.openhis.whale.booking.application.register.response.PagePatientsForSelectResponse;
import com.openhis.whale.booking.application.register.response.RescheduleBookingResponse;
import com.openhis.whale.booking.application.support.BookingAuditSupport;
import com.openhis.whale.booking.application.support.BookingOperatorResolver;
import com.openhis.whale.booking.application.support.BookingPagedResultBuilder;
import com.openhis.whale.booking.contracts.port.BookingPatientPort;
import com.openhis.whale.booking.domain.appointment.entity.Appointment;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentBookingDetailModel;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentBookingListModel;
import com.openhis.whale.booking.domain.appointment.service.AppointmentDomainService;
import com.openhis.whale.booking.domain.appointment.service.AppointmentQueryService;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleActorModel;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleCalendarItemModel;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleDetailModel;
import com.openhis.whale.booking.domain.schedule.service.ScheduleQueryService;
import com.openhis.whale.booking.domain.slot.queries.FindBookableSlotsQuery;
import com.openhis.whale.booking.domain.slot.queries.model.BookableSlotModel;
import com.openhis.whale.booking.domain.slot.queries.model.SlotDetailModel;
import com.openhis.whale.booking.application.outpatient.support.WeekTemplateMaterializeSupport;
import com.openhis.whale.booking.domain.slot.service.SlotDomainService;
import com.openhis.whale.booking.domain.slot.service.SlotQueryService;
import com.openhis.whale.booking.domain.template.queries.model.ResolvedDaySchedulePlan;
import com.openhis.whale.booking.domain.template.service.DayScheduleTemplateResolver;
import com.openhis.whale.booking.domain.waitlist.entity.WaitlistEntry;
import com.openhis.whale.booking.domain.waitlist.service.WaitlistDomainService;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 门诊预约登记应用服务。
 *
 * <p>职责边界：可约查询、创建/取消预约；占号经 SlotDomainService.allocateBooking。</p>
 * <p>接入：POST /api/app/bookingRegister/{methodName}</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class BookingRegisterAppService {

    private final SlotQueryService slotQueryService;
    private final DayScheduleTemplateResolver dayScheduleTemplateResolver;
    private final WeekTemplateMaterializeSupport weekTemplateMaterializeSupport;
    private final SlotDomainService slotDomainService;
    private final ScheduleQueryService scheduleQueryService;
    private final AppointmentDomainService appointmentDomainService;
    private final AppointmentQueryService appointmentQueryService;
    private final BookingPatientPort bookingPatientPort;
    private final BookingRegisterMapper bookingRegisterMapper;
    private final BookingOperatorResolver bookingOperatorResolver;
    private final BookingAuditSupport bookingAuditSupport;
    private final WaitlistDomainService waitlistDomainService;

    private static final String SLOT_BUSY_UNAVAILABLE = "busy-unavailable";

    /**
     * 按科室/医师/日期查可约 Slot（4.1）。
     */
    public ListBookableSlotsResponse listBookableSlots(@Valid ListBookableSlotsRequest request) {
        List<Long> scheduleIds = this.resolveScheduleIds(request);
        LocalDateTime dayStart = request.getDate().atStartOfDay();
        LocalDateTime dayEnd = request.getDate().plusDays(1).atStartOfDay();
        List<BookableSlotModel> merged = new ArrayList<>();
        List<SlotDetailModel> slotDetails = new ArrayList<>();

        for (Long scheduleId : scheduleIds) {
            FindBookableSlotsQuery slotQuery = new FindBookableSlotsQuery();
            slotQuery.setScheduleId(scheduleId);
            slotQuery.setStartTime(dayStart);
            slotQuery.setEndTime(dayEnd);
            List<BookableSlotModel> bookable = slotQueryService.findBookableSlots(slotQuery);
            for (BookableSlotModel model : bookable) {
                merged.add(model);
                slotDetails.add(slotQueryService.loadSlotById(model.getSlotId()));
            }
        }

        ListBookableSlotsResponse response = bookingRegisterMapper.toListBookableSlotsResponse(merged, slotDetails);
        this.enrichWaitlistMeta(response);
        if (scheduleIds.isEmpty()) {
            this.appendVirtualBookableSlots(response, request);
        }
        return response;
    }

    /**
     * 创建门诊预约：占号 + 写 Appointment（4.2）。
     */
    @Transactional
    public CreateOutpatientBookingResponse createOutpatientBooking(@Valid CreateOutpatientBookingRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        Long slotId = request.getSlotId();
        if (slotId == null) {
            if (request.getVirtualSlotKey() == null || request.getVirtualSlotKey().isBlank()) {
                throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 与 virtualSlotKey 须二选一");
            }
            slotId = weekTemplateMaterializeSupport.materializeVirtualSlot(request.getVirtualSlotKey(), operatorId);
        }
        SlotDetailModel slot = slotQueryService.loadSlotById(slotId);
        if (!request.getOrganizationId().equals(slot.getOrganizationId())) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "号源与机构不匹配");
        }

        Long destinationLocationId = request.getDestinationLocationId();
        if (destinationLocationId == null) {
            ScheduleDetailModel schedule = scheduleQueryService.loadScheduleById(slot.getScheduleId());
            destinationLocationId = bookingRegisterMapper.resolveLocationIdFromActors(schedule.getActors());
        }

        ScheduleCalendarItemModel scheduleMeta = scheduleQueryService.loadCalendarItemById(slot.getScheduleId());
        Long healthcareServiceId = scheduleMeta.getHealthcareServiceId();
        LocalDate bookingDate = slot.getStartTime() != null ? slot.getStartTime().toLocalDate() : LocalDate.now();
        if (healthcareServiceId != null
            && appointmentQueryService.findDuplicateOutpatientBooking(
                request.getPatientId(), bookingDate, healthcareServiceId, null).isPresent()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "同日同患者同服务重复预约");
        }

        slotDomainService.allocateBooking(bookingRegisterMapper.toAllocateBookingCommand(slotId, operatorId));
        Appointment appointment = appointmentDomainService.create(
            bookingRegisterMapper.toCreateAppointmentCommand(request, slot, destinationLocationId, operatorId));
        bookingAuditSupport.record("Appointment", appointment.getId(), "CREATE_OUTPATIENT_BOOKING", slotId.toString());
        return bookingRegisterMapper.toCreateOutpatientBookingResponse(appointment);
    }

    /**
     * 改约：释放旧号源、占用新号源并更新预约（4.4）。
     */
    @Transactional
    public RescheduleBookingResponse rescheduleBooking(@Valid RescheduleBookingRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        AppointmentBookingDetailModel booking = appointmentQueryService.loadOutpatientBookingById(request.getAppointmentId());
        SlotDetailModel newSlot = slotQueryService.loadSlotById(request.getNewSlotId());
        ScheduleCalendarItemModel newScheduleMeta = scheduleQueryService.loadCalendarItemById(newSlot.getScheduleId());
        LocalDate bookingDate = newSlot.getStartTime() != null ? newSlot.getStartTime().toLocalDate() : LocalDate.now();
        if (newScheduleMeta.getHealthcareServiceId() != null
            && appointmentQueryService.findDuplicateOutpatientBooking(
                booking.getPatientId(), bookingDate, newScheduleMeta.getHealthcareServiceId(), booking.getId()).isPresent()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "同日同患者同服务重复预约");
        }
        if (booking.getSlotId() != null) {
            slotDomainService.releaseBooking(
                bookingRegisterMapper.toReleaseBookingCommand(booking.getSlotId(), operatorId));
            this.tryPromoteWaitlist(booking.getSlotId(), operatorId);
        }
        slotDomainService.allocateBooking(
            bookingRegisterMapper.toAllocateBookingCommand(request.getNewSlotId(), operatorId));
        appointmentDomainService.rescheduleOutpatientBooking(
            bookingRegisterMapper.toRescheduleOutpatientBookingCommand(request, newSlot, operatorId));
        return bookingRegisterMapper.toRescheduleBookingResponse(request.getAppointmentId(), request.getNewSlotId());
    }

    /**
     * 取消预约：预约终态 + 释放号源（4.3）。
     */
    @Transactional
    public CancelBookingResponse cancelBooking(@Valid CancelBookingRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        AppointmentBookingDetailModel booking = appointmentQueryService.loadOutpatientBookingById(request.getAppointmentId());
        if (booking.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "预约未关联号源，无法释放");
        }
        Long slotId = booking.getSlotId();
        appointmentDomainService.cancelAppointment(
            bookingRegisterMapper.toCancelAppointmentCommand(request, operatorId));
        slotDomainService.releaseBooking(
            bookingRegisterMapper.toReleaseBookingCommand(slotId, operatorId));
        bookingAuditSupport.record("Appointment", request.getAppointmentId(), "CANCEL_BOOKING", request.getReason());
        CancelBookingResponse response = new CancelBookingResponse();
        Optional<Appointment> promoted = this.tryPromoteWaitlist(slotId, operatorId);
        if (promoted.isPresent()) {
            response.setWaitlistPromoted(true);
            response.setPromotedAppointmentId(promoted.get().getId());
            response.setPromotedBusNo(promoted.get().getBusNo());
        } else {
            response.setWaitlistPromoted(false);
        }
        return response;
    }

    /**
     * 加入候补队列（BR-EXT-011）：号源已满时不占 slot。
     */
    @Transactional
    public JoinWaitlistResponse joinWaitlist(@Valid JoinWaitlistRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        SlotDetailModel slot = slotQueryService.loadSlotById(request.getSlotId());
        if (!request.getOrganizationId().equals(slot.getOrganizationId())) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "号源与机构不匹配");
        }
        if (SLOT_BUSY_UNAVAILABLE.equals(slot.getStatusCode())) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "号源已停诊，不可候补");
        }
        if (!this.isSlotFull(slot)) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "号源未满，请直接预约");
        }
        ScheduleCalendarItemModel scheduleMeta = scheduleQueryService.loadCalendarItemById(slot.getScheduleId());
        Long healthcareServiceId = scheduleMeta.getHealthcareServiceId();
        LocalDate bookingDate = slot.getStartTime() != null ? slot.getStartTime().toLocalDate() : LocalDate.now();
        if (healthcareServiceId != null
            && appointmentQueryService.findDuplicateOutpatientBooking(
                request.getPatientId(), bookingDate, healthcareServiceId, null).isPresent()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "同日同患者同服务已有预约");
        }
        WaitlistEntry entry = waitlistDomainService.join(
            request.getOrganizationId(),
            request.getPatientId(),
            request.getSlotId(),
            slot.getScheduleId(),
            operatorId);
        bookingAuditSupport.record("WaitlistEntry", entry.getId(), "JOIN_WAITLIST", request.getSlotId().toString());
        JoinWaitlistResponse response = new JoinWaitlistResponse();
        response.setWaitlistEntryId(entry.getId());
        response.setSlotId(entry.getSlotId());
        response.setQueuePosition(entry.getQueuePosition());
        response.setWaitingCount(waitlistDomainService.countWaitingBySlotId(entry.getSlotId()));
        return response;
    }

    /**
     * 取消候补。
     */
    @Transactional
    public void cancelWaitlist(@Valid CancelWaitlistRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        waitlistDomainService.cancel(request.getWaitlistEntryId(), request.getReason(), operatorId);
        bookingAuditSupport.record("WaitlistEntry", request.getWaitlistEntryId(), "CANCEL_WAITLIST", request.getReason());
    }

    /**
     * 预约列表分页（4.5）。
     */
    public PageBookingsResponse pageBookings(@Valid PageBookingsRequest request) {
        Page<AppointmentBookingListModel> page = appointmentQueryService.pageOutpatientBookings(
            bookingRegisterMapper.toAppointmentPageQuery(request));
        Map<Long, String> patientNames = bookingPatientPort.findPatientNamesByIds(this.collectPatientIds(page.getContent()));
        List<BookingListItemDto> rows = new ArrayList<>();
        for (AppointmentBookingListModel model : page.getContent()) {
            BookingListItemDto row = bookingRegisterMapper.toBookingListItemDto(model);
            row.setPatientName(patientNames.get(model.getPatientId()));
            rows.add(row);
        }
        return BookingPagedResultBuilder.build(new PageBookingsResponse(), rows, page);
    }

    /**
     * 预约详情（4.6）。
     */
    public BookingDetailDto loadBookingDetail(@Valid LoadBookingDetailRequest request) {
        AppointmentBookingDetailModel booking = appointmentQueryService.loadOutpatientBookingById(request.getAppointmentId());
        SlotDetailModel slot = null;
        List<ScheduleActorModel> actors = new ArrayList<>();
        if (booking.getSlotId() != null) {
            slot = slotQueryService.loadSlotById(booking.getSlotId());
            if (slot.getScheduleId() != null) {
                ScheduleDetailModel schedule = scheduleQueryService.loadScheduleById(slot.getScheduleId());
                if (schedule.getActors() != null) {
                    actors = schedule.getActors();
                }
            }
        }
        BookingDetailDto detail = bookingRegisterMapper.toBookingDetailDto(booking, slot, actors);
        if (booking.getPatientId() != null) {
            Map<Long, String> patientNames = bookingPatientPort.findPatientNamesByIds(List.of(booking.getPatientId()));
            detail.setPatientName(patientNames.get(booking.getPatientId()));
        }
        return detail;
    }

    /**
     * 患者检索（4.7），委托 BookingPatientPort。
     */
    public PagePatientsForSelectResponse pagePatientsForSelect(@Valid PagePatientsForSelectRequest request) {
        return bookingRegisterMapper.toPagePatientsForSelectResponse(
            bookingPatientPort.pagePatientsForSelect(bookingRegisterMapper.toBookingPatientSearchQuery(request)));
    }

    private List<Long> resolveScheduleIds(ListBookableSlotsRequest request) {
        if (request.getScheduleId() != null) {
            List<Long> single = new ArrayList<>();
            single.add(request.getScheduleId());
            return single;
        }
        List<ScheduleCalendarItemModel> schedules = scheduleQueryService.listSchedulesForCalendar(
            bookingRegisterMapper.toCalendarScheduleQuery(
                request.getOrganizationId(),
                request.getDate(),
                request.getPractitionerId(),
                request.getHealthcareServiceId()));
        return bookingRegisterMapper.toScheduleIds(schedules);
    }

    private List<Long> collectPatientIds(List<AppointmentBookingListModel> rows) {
        Set<Long> ids = new LinkedHashSet<>();
        for (AppointmentBookingListModel row : rows) {
            if (row.getPatientId() != null) {
                ids.add(row.getPatientId());
            }
        }
        return new ArrayList<>(ids);
    }

    private void appendVirtualBookableSlots(ListBookableSlotsResponse response, ListBookableSlotsRequest request) {
        List<ResolvedDaySchedulePlan> plans = dayScheduleTemplateResolver.resolveWeekFallbackPlans(
            request.getOrganizationId(),
            request.getDate(),
            request.getPractitionerId(),
            null,
            request.getHealthcareServiceId());
        if (response.getSlots() == null) {
            response.setSlots(new ArrayList<>());
        }
        for (ResolvedDaySchedulePlan plan : plans) {
            if (!plan.isBookable() || plan.getVirtualPeriods() == null) {
                continue;
            }
            for (ResolvedDaySchedulePlan.VirtualSlotPeriod period : plan.getVirtualPeriods()) {
                response.getSlots().add(bookingRegisterMapper.toVirtualBookableSlotDto(period));
            }
        }
    }

    private void enrichWaitlistMeta(ListBookableSlotsResponse response) {
        if (response.getSlots() == null || response.getSlots().isEmpty()) {
            return;
        }
        List<Long> slotIds = new ArrayList<>();
        for (var dto : response.getSlots()) {
            if (dto.getSlotId() != null) {
                slotIds.add(dto.getSlotId());
            }
        }
        Map<Long, Long> counts = waitlistDomainService.countWaitingBySlotIds(slotIds);
        for (var dto : response.getSlots()) {
            if (Boolean.TRUE.equals(dto.getFullFlag()) && dto.getSlotId() != null) {
                dto.setWaitlistEligible(true);
            }
            if (dto.getSlotId() != null) {
                dto.setWaitlistCount(counts.getOrDefault(dto.getSlotId(), 0L));
            }
        }
    }

    private Optional<Appointment> tryPromoteWaitlist(Long slotId, Long operatorId) {
        if (slotId == null) {
            return Optional.empty();
        }
        SlotDetailModel slot = slotQueryService.loadSlotById(slotId);
        if (this.isSlotFull(slot) || SLOT_BUSY_UNAVAILABLE.equals(slot.getStatusCode())) {
            return Optional.empty();
        }
        Optional<WaitlistEntry> next = waitlistDomainService.findNextWaiting(slotId);
        if (next.isEmpty()) {
            return Optional.empty();
        }
        WaitlistEntry entry = next.get();
        ScheduleCalendarItemModel scheduleMeta = scheduleQueryService.loadCalendarItemById(slot.getScheduleId());
        Long healthcareServiceId = scheduleMeta.getHealthcareServiceId();
        LocalDate bookingDate = slot.getStartTime() != null ? slot.getStartTime().toLocalDate() : LocalDate.now();
        if (healthcareServiceId != null
            && appointmentQueryService.findDuplicateOutpatientBooking(
                entry.getPatientId(), bookingDate, healthcareServiceId, null).isPresent()) {
            waitlistDomainService.cancel(entry.getId(), "同日重复预约，自动跳过", operatorId);
            return this.tryPromoteWaitlist(slotId, operatorId);
        }
        CreateOutpatientBookingRequest promoteRequest = new CreateOutpatientBookingRequest();
        promoteRequest.setOrganizationId(entry.getOrganizationId());
        promoteRequest.setPatientId(entry.getPatientId());
        promoteRequest.setSlotId(slotId);
        promoteRequest.setOriginChannel("WAITLIST");
        Long destinationLocationId = null;
        ScheduleDetailModel schedule = scheduleQueryService.loadScheduleById(slot.getScheduleId());
        destinationLocationId = bookingRegisterMapper.resolveLocationIdFromActors(schedule.getActors());
        slotDomainService.allocateBooking(bookingRegisterMapper.toAllocateBookingCommand(slotId, operatorId));
        Appointment appointment = appointmentDomainService.create(
            bookingRegisterMapper.toCreateAppointmentCommand(promoteRequest, slot, destinationLocationId, operatorId));
        waitlistDomainService.markPromoted(entry, appointment.getId(), operatorId);
        bookingAuditSupport.record("Appointment", appointment.getId(), "PROMOTE_WAITLIST", slotId.toString());
        return Optional.of(appointment);
    }

    private boolean isSlotFull(SlotDetailModel slot) {
        return slot.getCapacity() != null
            && slot.getBookedCount() != null
            && slot.getBookedCount() >= slot.getCapacity();
    }
}
