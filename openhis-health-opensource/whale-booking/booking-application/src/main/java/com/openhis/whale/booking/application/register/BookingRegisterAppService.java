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
import com.openhis.whale.booking.application.support.OutpatientBookingWriteSupport;
import com.openhis.whale.booking.application.support.OutpatientWaitlistPromoteSupport;
import com.openhis.whale.booking.contracts.reader.HealthPatientReader;
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
import com.openhis.whale.booking.domain.share.BookingDateSupport;
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
 * 门诊预约登记应用服务（IF 4.x）。
 *
 * <p>职责边界：可约号源查询、创建/改约/取消预约、候补队列（BR-EXT-011）；占号经 {@link SlotDomainService#allocateBooking}。</p>
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/bookingRegister/{methodName}}。</p>
 * <p>虚拟可约：无 Schedule 时经 TW 模板解析 virtualSlotKey 并物化为真实 slot。</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class BookingRegisterAppService {

    /** 号源读侧：可约列表、详情、容量事实。 */
    private final SlotQueryService slotQueryService;
    /** T2 日模板解析：无 Schedule 时的虚拟时段计划。 */
    private final DayScheduleTemplateResolver dayScheduleTemplateResolver;
    /** 虚拟 slot 物化为 wkf_slot 并占用。 */
    private final WeekTemplateMaterializeSupport weekTemplateMaterializeSupport;
    /** 号源写侧：allocate/release。 */
    private final SlotDomainService slotDomainService;
    /** 日程读侧：参与者、号源类型、重复预约校验上下文。 */
    private final ScheduleQueryService scheduleQueryService;
    /** 预约写侧：创建、取消、改约。 */
    private final AppointmentDomainService appointmentDomainService;
    /** 预约读侧：分页、详情、重复预约检测。 */
    private final AppointmentQueryService appointmentQueryService;
    /** 患者主数据（health 经 Reader/Writer/Client）。 */
    private final HealthPatientReader healthPatientReader;
    /** Request/Command/DTO 映射。 */
    private final BookingRegisterMapper bookingRegisterMapper;
    /** 写路径操作人。 */
    private final BookingOperatorResolver bookingOperatorResolver;
    /** 审计日志。 */
    private final BookingAuditSupport bookingAuditSupport;
    /** 候补队列写侧。 */
    private final WaitlistDomainService waitlistDomainService;
    /** 门诊预约占号 + 建单（防重 DB 兜底）。 */
    private final OutpatientBookingWriteSupport outpatientBookingWriteSupport;
    /** 候补自动提升。 */
    private final OutpatientWaitlistPromoteSupport outpatientWaitlistPromoteSupport;

    /** 停诊号源状态码，满号候补时须排除。 */
    private static final String SLOT_BUSY_UNAVAILABLE = "busy-unavailable";

    /**
     * 按科室/医师/日期查可约 Slot（IF-4.1）。
     *
     * @param request 机构、日期、可选 scheduleId/医师/号源类型
     * @return 可约时段列表（含满号候补元数据）；无 Schedule 时追加 TW 虚拟可约
     */
    public ListBookableSlotsResponse listBookableSlots(@Valid ListBookableSlotsRequest request) {
        // Pre：解析当日关联 scheduleId 列表（单 schedule 或日历过滤）。
        List<Long> scheduleIds = this.resolveScheduleIds(request);
        LocalDateTime dayStart = request.getDate().atStartOfDay();
        LocalDateTime dayEnd = request.getDate().plusDays(1).atStartOfDay();
        List<BookableSlotModel> merged = new ArrayList<>();
        List<SlotDetailModel> slotDetails = new ArrayList<>();

        // Pre：逐 schedule 查 free 且有余量的 slot。
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

        // Asm：映射 DTO 并附加候补人数/可候补标记。
        ListBookableSlotsResponse response = bookingRegisterMapper.toListBookableSlotsResponse(merged, slotDetails);
        this.enrichWaitlistMeta(response);
        // 无真实 Schedule 时合并 TW 虚拟可约段（virtualSlotKey 预约入口）。
        if (scheduleIds.isEmpty()) {
            this.appendVirtualBookableSlots(response, request);
        }
        return response;
    }

    /**
     * 创建门诊预约：占号 + 写 Appointment（IF-4.2）。
     *
     * @param request 患者、slotId 或 virtualSlotKey、机构等
     * @return 新预约主键与业务号
     * @throws BusinessException 机构不匹配、同日重复预约、号源不可用
     */
    public CreateOutpatientBookingResponse createOutpatientBooking(@Valid CreateOutpatientBookingRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        Long slotId = request.getSlotId();
        // Pre：virtualSlotKey 路径先物化 slot（TW 模板 → wkf_slot）。
        if (slotId == null) {
            if (request.getVirtualSlotKey() == null || request.getVirtualSlotKey().isBlank()) {
                throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "slotId 与 virtualSlotKey 须二选一");
            }
            slotId = weekTemplateMaterializeSupport.materializeVirtualSlot(request.getVirtualSlotKey(), operatorId);
        }
        SlotDetailModel slot = slotQueryService.loadSlotById(slotId);
        // Gate：号源须归属请求机构。
        if (!request.getOrganizationId().equals(slot.getOrganizationId())) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "号源与机构不匹配");
        }

        Long destinationLocationId = request.getDestinationLocationId();
        if (destinationLocationId == null) {
            ScheduleDetailModel schedule = scheduleQueryService.loadScheduleById(slot.getScheduleId());
            destinationLocationId = bookingRegisterMapper.resolveLocationIdFromActors(schedule.getActors());
        }

        // Gate：同日同患者同号源类型不可重复预约（BR-APT 规则）。
        ScheduleCalendarItemModel scheduleMeta = scheduleQueryService.loadCalendarItemById(slot.getScheduleId());
        Long healthcareServiceId = scheduleMeta.getHealthcareServiceId();
        LocalDate bookingDate = BookingDateSupport.resolveBookingDate(slot.getStartTime());
        outpatientBookingWriteSupport.assertNoDuplicateOutpatientBooking(
            request.getPatientId(), bookingDate, healthcareServiceId, null);

        CreateOutpatientBookingRequest createRequest = request;
        createRequest.setSlotId(slotId);
        Appointment appointment = outpatientBookingWriteSupport.allocateAndCreate(
            bookingRegisterMapper.toCreateAppointmentCommand(
                createRequest, slot, slotId, destinationLocationId, healthcareServiceId, bookingDate, operatorId),
            slotId,
            operatorId);
        bookingAuditSupport.record("Appointment", appointment.getId(), "CREATE_OUTPATIENT_BOOKING", slotId.toString());
        // Asm：映射创建预约响应。
        return bookingRegisterMapper.toCreateOutpatientBookingResponse(appointment);
    }

    /**
     * 改约：释放旧号源、占用新号源并更新预约（IF-4.4）。
     *
     * @param request 预约主键与新 slotId
     * @return 改约结果
     */
    public RescheduleBookingResponse rescheduleBooking(@Valid RescheduleBookingRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：加载原预约与新号源及日程元数据。
        AppointmentBookingDetailModel booking = appointmentQueryService.loadOutpatientBookingById(request.getAppointmentId());
        SlotDetailModel newSlot = slotQueryService.loadSlotById(request.getNewSlotId());
        ScheduleCalendarItemModel newScheduleMeta = scheduleQueryService.loadCalendarItemById(newSlot.getScheduleId());
        LocalDate bookingDate = BookingDateSupport.resolveBookingDate(newSlot.getStartTime());
        outpatientBookingWriteSupport.assertNoDuplicateOutpatientBooking(
            booking.getPatientId(), bookingDate, newScheduleMeta.getHealthcareServiceId(), booking.getId());
        // Cmd：释放旧 slot；若有候补则尝试提升队首。
        if (booking.getSlotId() != null) {
            slotDomainService.releaseBooking(
                bookingRegisterMapper.toReleaseBookingCommand(booking.getSlotId(), operatorId));
            outpatientWaitlistPromoteSupport.tryPromoteWaitlist(booking.getSlotId(), operatorId);
        }
        slotDomainService.allocateBooking(
            bookingRegisterMapper.toAllocateBookingCommand(request.getNewSlotId(), operatorId));
        appointmentDomainService.rescheduleOutpatientBooking(
            bookingRegisterMapper.toRescheduleOutpatientBookingCommand(
                request, newSlot, newScheduleMeta.getHealthcareServiceId(), bookingDate, operatorId));
        // Asm：映射改约响应。
        return bookingRegisterMapper.toRescheduleBookingResponse(request.getAppointmentId(), request.getNewSlotId());
    }

    /**
     * 取消预约：预约终态 + 释放号源（IF-4.3）。
     *
     * @param request 预约主键与取消原因
     * @return 是否触发候补提升及新预约信息
     */
    public CancelBookingResponse cancelBooking(@Valid CancelBookingRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：加载预约并校验已关联号源。
        AppointmentBookingDetailModel booking = appointmentQueryService.loadOutpatientBookingById(request.getAppointmentId());
        if (booking.getSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "预约未关联号源，无法释放");
        }
        Long slotId = booking.getSlotId();
        // Cmd：先取消 appointment 再释放 slot 容量。
        appointmentDomainService.cancelAppointment(
            bookingRegisterMapper.toCancelAppointmentCommand(request, operatorId));
        slotDomainService.releaseBooking(
            bookingRegisterMapper.toReleaseBookingCommand(slotId, operatorId));
        bookingAuditSupport.record("Appointment", request.getAppointmentId(), "CANCEL_BOOKING", request.getReason());
        CancelBookingResponse response = new CancelBookingResponse();
        // Post：释放容量后按 queue_position 提升候补（BR-EXT-011）。
        Optional<Appointment> promoted = outpatientWaitlistPromoteSupport.tryPromoteWaitlist(slotId, operatorId);
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
     *
     * @param request 患者、slotId、机构
     * @return 候补条目与排队序号
     */
    public JoinWaitlistResponse joinWaitlist(@Valid JoinWaitlistRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：加载号源并校验机构归属。
        SlotDetailModel slot = slotQueryService.loadSlotById(request.getSlotId());
        if (!request.getOrganizationId().equals(slot.getOrganizationId())) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "号源与机构不匹配");
        }
        // Gate：停诊号源不可候补；未满应直接预约。
        if (SLOT_BUSY_UNAVAILABLE.equals(slot.getStatusCode())) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "号源已停诊，不可候补");
        }
        if (!this.isSlotFull(slot)) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "号源未满，请直接预约");
        }
        ScheduleCalendarItemModel scheduleMeta = scheduleQueryService.loadCalendarItemById(slot.getScheduleId());
        Long healthcareServiceId = scheduleMeta.getHealthcareServiceId();
        LocalDate bookingDate = BookingDateSupport.resolveBookingDate(slot.getStartTime());
        outpatientBookingWriteSupport.assertNoDuplicateOutpatientBooking(
            request.getPatientId(), bookingDate, healthcareServiceId, null);
        // Cmd：写入候补队列（不占 bookedCount）。
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
        // Asm：映射候补入队响应。
        return response;
    }

    /**
     * 取消候补（IF-4.x）。
     *
     * @param request 候补条目主键与原因
     */
    public void cancelWaitlist(@Valid CancelWaitlistRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：取消候补条目并记录审计。
        waitlistDomainService.cancel(request.getWaitlistEntryId(), request.getReason(), operatorId);
        bookingAuditSupport.record("WaitlistEntry", request.getWaitlistEntryId(), "CANCEL_WAITLIST", request.getReason());
    }

    /**
     * 预约列表分页（IF-4.5）。
     *
     * @param request 分页与筛选条件
     * @return 分页列表（含患者姓名回填）
     */
    public PageBookingsResponse pageBookings(@Valid PageBookingsRequest request) {
        Page<AppointmentBookingListModel> page = appointmentQueryService.pageOutpatientBookings(
            bookingRegisterMapper.toAppointmentPageQuery(request));
        // Pre：批量解析患者姓名，避免 N+1。
        Map<Long, String> patientNames = healthPatientReader.findPatientNamesByIds(this.collectPatientIds(page.getContent()));
        List<BookingListItemDto> rows = new ArrayList<>();
        for (AppointmentBookingListModel model : page.getContent()) {
            BookingListItemDto row = bookingRegisterMapper.toBookingListItemDto(model);
            row.setPatientName(patientNames.get(model.getPatientId()));
            rows.add(row);
        }
        // Asm：包装预约列表分页结果。
        return BookingPagedResultBuilder.build(new PageBookingsResponse(), rows, page);
    }

    /**
     * 预约详情（IF-4.6）。
     *
     * @param request 预约主键
     * @return 详情 DTO（含 slot、参与者、患者名）
     */
    public BookingDetailDto loadBookingDetail(@Valid LoadBookingDetailRequest request) {
        // Pre：加载预约详情并关联号源、日程参与者。
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
            Map<Long, String> patientNames = healthPatientReader.findPatientNamesByIds(List.of(booking.getPatientId()));
            detail.setPatientName(patientNames.get(booking.getPatientId()));
        }
        // Asm：返回含患者姓名的详情 DTO。
        return detail;
    }

    /**
     * 患者检索（IF-4.7），委托 {@link HealthPatientReader}。
     *
     * @param request 关键字分页
     * @return 患者下拉分页
     */
    public PagePatientsForSelectResponse pagePatientsForSelect(@Valid PagePatientsForSelectRequest request) {
        // Pre：委托患者主数据 Reader 分页检索。
        // Asm：映射患者下拉分页响应。
        return bookingRegisterMapper.toPagePatientsForSelectResponse(
            healthPatientReader.pagePatientsForSelect(bookingRegisterMapper.toBookingPatientSearchQuery(request)));
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

    /** 号源 bookedCount 已达 capacity 视为满号。 */
    private boolean isSlotFull(SlotDetailModel slot) {
        return slot.getCapacity() != null
            && slot.getBookedCount() != null
            && slot.getBookedCount() >= slot.getCapacity();
    }
}
