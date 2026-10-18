/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.PeisContractTypeCodes;
import com.zxplat.plugin.booking.application.healthcheckup.dto.HcBookableDayDto;
import com.zxplat.plugin.booking.application.healthcheckup.dto.HcBookableSlotDto;
import com.zxplat.plugin.booking.application.healthcheckup.mapper.HealthCheckupSlotCalendarMapper;
import com.zxplat.plugin.booking.application.healthcheckup.request.CancelHcSlotBookingRequest;
import com.zxplat.plugin.booking.application.healthcheckup.request.CreateHcSlotBookingRequest;
import com.zxplat.plugin.booking.application.healthcheckup.request.ListHcBookableDaysRequest;
import com.zxplat.plugin.booking.application.healthcheckup.request.ListHcBookableSlotsRequest;
import com.zxplat.plugin.booking.application.healthcheckup.request.RescheduleHcSlotBookingRequest;
import com.zxplat.plugin.booking.application.healthcheckup.response.CancelHcSlotBookingResponse;
import com.zxplat.plugin.booking.application.healthcheckup.response.CreateHcSlotBookingResponse;
import com.zxplat.plugin.booking.application.healthcheckup.response.ListHcBookableDaysResponse;
import com.zxplat.plugin.booking.application.healthcheckup.response.ListHcBookableSlotsResponse;
import com.zxplat.plugin.booking.application.healthcheckup.response.RescheduleHcSlotBookingResponse;
import com.zxplat.plugin.booking.application.healthcheckup.support.BookingCampusSupport;
import com.zxplat.plugin.booking.application.share.BookingOperatorResolver;
import com.zxplat.plugin.booking.domain.appointment.command.CancelAppointmentCommand;
import com.zxplat.plugin.booking.domain.appointment.entity.Appointment;
import com.zxplat.plugin.booking.domain.appointment.repository.AppointmentRepository;
import com.zxplat.plugin.booking.domain.appointment.service.AppointmentDomainService;
import com.zxplat.plugin.booking.domain.appointment.service.AppointmentQueryService;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.HcDayPlan;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.HcDaySlot;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.HcDayType;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.HealthCheckBookingExt;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.HealthCheckPackage;
import com.zxplat.plugin.booking.domain.healthcheckup.repository.HealthCheckBookingExtRepository;
import com.zxplat.plugin.booking.domain.healthcheckup.service.HealthCheckupDomainService;
import com.zxplat.plugin.booking.domain.healthcheckup.service.HealthCheckupQueryService;
import com.zxplat.plugin.booking.domain.healthcheckup.service.HealthCheckupSlotCalendarDomainService;
import com.zxplat.plugin.booking.domain.healthcheckup.service.HealthCheckupSlotCalendarQueryService;
import com.zxplat.plugin.booking.domain.share.AppointmentKindConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 体检号源占释号应用服务（18 号需求 · 窗口/小程序）。
 *
 * <p>contractTypeCode 由前端从 peis 合同列表取得后传入；本服务不直连 peis。</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class HealthCheckupSlotOccupyAppService {

    private static final String HEALTH_CHECKUP_BOOKING_KIND = AppointmentKindConstants.HEALTH_CHECKUP_BOOKING;

    /** 号源日历读侧。 */
    private final HealthCheckupSlotCalendarQueryService slotCalendarQueryService;
    /** 号源日历写侧（占释号）。 */
    private final HealthCheckupSlotCalendarDomainService slotCalendarDomainService;
    /** 体检读侧（套餐）。 */
    private final HealthCheckupQueryService healthCheckupQueryService;
    /** 体检写侧（扩展）。 */
    private final HealthCheckupDomainService healthCheckupDomainService;
    /** 预约写侧。 */
    private final AppointmentDomainService appointmentDomainService;
    /** 预约读侧。 */
    private final AppointmentQueryService appointmentQueryService;
    /** 预约仓储（改约更新时段快照）。 */
    private final AppointmentRepository appointmentRepository;
    /** 体检扩展仓储。 */
    private final HealthCheckBookingExtRepository bookingExtRepository;
    /** Request/DTO 映射。 */
    private final HealthCheckupSlotCalendarMapper slotCalendarMapper;
    /** 写路径操作人。 */
    private final BookingOperatorResolver bookingOperatorResolver;
    /** 校区模块开关。 */
    private final BookingCampusSupport bookingCampusSupport;

    /**
     * 列出可约日期（有余量、非休息、类型开放）。
     *
     * @param request 机构、合同类型与日期区间
     * @return 可约日期列表
     */
    public ListHcBookableDaysResponse listBookableDays(@Valid ListHcBookableDaysRequest request) {
        // Pre：校验日期区间并加载区间内日计划与时段。
        if (request.getToDate().isBefore(request.getFromDate())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "结束日期不得早于起始日期");
        }
        List<HcDayPlan> plans = slotCalendarQueryService.listDayPlansBetween(
            request.getOrganizationId(), request.getFromDate(), request.getToDate());
        List<HcDaySlot> allSlots = slotCalendarQueryService.listDaySlotsByDates(
            request.getOrganizationId(), this.buildDateList(request.getFromDate(), request.getToDate()));
        String campusFilter = this.resolveCampusCodeForRead(request.getCampusCode());
        List<HcBookableDayDto> rows = new ArrayList<>();
        LocalDate cursor = request.getFromDate();
        while (!cursor.isAfter(request.getToDate())) {
            HcDayPlan plan = this.findPlanForDate(plans, cursor);
            int remaining = this.sumBookableRemaining(plan, allSlots, cursor, request.getContractTypeCode(), campusFilter);
            if (remaining > 0) {
                HcBookableDayDto dto = new HcBookableDayDto();
                dto.setPlanDate(cursor);
                dto.setRemaining(remaining);
                rows.add(dto);
            }
            cursor = cursor.plusDays(1);
        }
        ListHcBookableDaysResponse response = new ListHcBookableDaysResponse();
        response.setDays(rows);
        // Asm：返回可约日期。
        return response;
    }

    /**
     * 列出指定日可约时段。
     *
     * @param request 机构、日期、合同类型与可选校区
     * @return 可约时段列表
     */
    public ListHcBookableSlotsResponse listBookableSlots(@Valid ListHcBookableSlotsRequest request) {
        // Pre：加载日计划并校验非休息、类型开放。
        Optional<HcDayPlan> planOpt = slotCalendarQueryService.findDayPlan(request.getOrganizationId(), request.getPlanDate());
        if (planOpt.isEmpty()) {
            ListHcBookableSlotsResponse empty = new ListHcBookableSlotsResponse();
            empty.setSlots(List.of());
            return empty;
        }
        HcDayPlan plan = planOpt.get();
        if (Boolean.TRUE.equals(plan.getRestFlag())) {
            ListHcBookableSlotsResponse empty = new ListHcBookableSlotsResponse();
            empty.setSlots(List.of());
            return empty;
        }
        if (!this.isContractTypeAllowed(plan.getId(), request.getContractTypeCode())) {
            ListHcBookableSlotsResponse empty = new ListHcBookableSlotsResponse();
            empty.setSlots(List.of());
            return empty;
        }
        String campusFilter = this.resolveCampusCodeForRead(request.getCampusCode());
        List<HcDaySlot> slots = slotCalendarQueryService.listDaySlotsByDate(
            request.getOrganizationId(), request.getPlanDate());
        List<HcBookableSlotDto> rows = new ArrayList<>();
        for (HcDaySlot slot : slots) {
            if (!this.isBookableSlot(slot, request.getContractTypeCode(), campusFilter)) {
                continue;
            }
            rows.add(slotCalendarMapper.toHcBookableSlotDto(slot));
        }
        ListHcBookableSlotsResponse response = new ListHcBookableSlotsResponse();
        response.setSlots(rows);
        // Asm：返回可约时段。
        return response;
    }

    /**
     * 创建体检号源预约：占号 + 写 Appointment + 写扩展。
     *
     * <p>contractTypeCode 由 UI 从 peis 有效合同列表取得；无合同时服务端强制 WALK_IN。</p>
     *
     * @param request 患者、套餐、时段与合同信息
     * @return 新预约主键与业务号
     */
    @Transactional
    public CreateHcSlotBookingResponse createBooking(@Valid CreateHcSlotBookingRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：解析合同类型、加载套餐与时段并校验可约性。
        String resolvedTypeCode = this.resolveContractTypeCode(request);
        HealthCheckPackage pkg = healthCheckupQueryService.loadPackageById(request.getPackageId());
        HcDaySlot daySlot = slotCalendarQueryService.loadDaySlotById(request.getDaySlotId());
        this.assertDaySlotBookable(request, daySlot, resolvedTypeCode);
        String campusCode = daySlot.getCampusCode();
        // Cmd：占号、建预约、写扩展。
        slotCalendarDomainService.occupySlot(daySlot.getId(), operatorId);
        Appointment appointment = appointmentDomainService.create(
            slotCalendarMapper.toHcSlotBookingAppointmentCommand(request, daySlot, operatorId));
        healthCheckupDomainService.saveBookingExt(
            slotCalendarMapper.toHcSlotBookingExt(
                appointment.getId(), request, resolvedTypeCode, campusCode, pkg),
            operatorId);
        // Asm：映射创建响应。
        return slotCalendarMapper.toCreateHcSlotBookingResponse(appointment);
    }

    /**
     * 取消体检号源预约：释号 + 取消 Appointment。
     *
     * @param request 预约主键与取消原因
     * @return 取消结果
     */
    @Transactional
    public CancelHcSlotBookingResponse cancelBooking(@Valid CancelHcSlotBookingRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：校验体检预约并加载扩展。
        this.requireHealthCheckupAppointment(request.getAppointmentId());
        HealthCheckBookingExt ext = bookingExtRepository.findByAppointmentIdAndIsDeletedFalse(request.getAppointmentId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "体检预约扩展不存在"));
        // Cmd：释号。
        if (ext.getDaySlotId() != null) {
            slotCalendarDomainService.releaseSlot(ext.getDaySlotId(), operatorId);
        }
        CancelAppointmentCommand command = new CancelAppointmentCommand();
        command.setOperatorId(operatorId);
        CancelAppointmentCommand.CancelAppointmentItemCommand item = new CancelAppointmentCommand.CancelAppointmentItemCommand();
        item.setAppointmentId(request.getAppointmentId());
        item.setReason(request.getReason());
        item.setCancellationReasonCode(request.getCancellationReasonCode());
        command.setItem(item);
        // Cmd：取消预约。
        appointmentDomainService.cancelAppointment(command);
        CancelHcSlotBookingResponse response = new CancelHcSlotBookingResponse();
        response.setAppointmentId(request.getAppointmentId());
        // Asm：返回取消结果。
        return response;
    }

    /**
     * 改约：先释旧号再占新号，同事务更新预约时段快照。
     *
     * @param request 预约主键与新时段配额主键
     * @return 改约结果
     */
    @Transactional
    public RescheduleHcSlotBookingResponse rescheduleBooking(@Valid RescheduleHcSlotBookingRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：校验预约与扩展，加载新旧时段。
        Appointment appointment = this.requireHealthCheckupAppointmentEntity(request.getAppointmentId());
        appointment.assertActive();
        HealthCheckBookingExt ext = bookingExtRepository.findByAppointmentIdAndIsDeletedFalse(request.getAppointmentId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "体检预约扩展不存在"));
        HcDaySlot newSlot = slotCalendarQueryService.loadDaySlotById(request.getNewDaySlotId());
        CreateHcSlotBookingRequest validateRequest = new CreateHcSlotBookingRequest();
        validateRequest.setOrganizationId(appointment.getOrganizationId());
        validateRequest.setDaySlotId(newSlot.getId());
        validateRequest.setContractId(ext.getContractId());
        validateRequest.setContractTypeCode(ext.getContractTypeCode());
        this.assertDaySlotBookable(validateRequest, newSlot, ext.getContractTypeCode());
        // Cmd：释旧占新。
        if (ext.getDaySlotId() != null) {
            slotCalendarDomainService.releaseSlot(ext.getDaySlotId(), operatorId);
        }
        slotCalendarDomainService.occupySlot(newSlot.getId(), operatorId);
        ext.setDaySlotId(newSlot.getId());
        ext.setCampusCode(newSlot.getCampusCode());
        healthCheckupDomainService.saveBookingExt(ext, operatorId);
        appointment.setBookingDate(newSlot.getPlanDate());
        appointment.setStartInstant(LocalDateTime.of(newSlot.getPlanDate(), newSlot.getStartTime()));
        appointment.setEndInstant(LocalDateTime.of(newSlot.getPlanDate(), newSlot.getEndTime()));
        appointment.setUpdatedAt(LocalDateTime.now());
        appointment.setUpdatedBy(operatorId);
        try {
            appointmentRepository.save(appointment);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "预约已被修改，请刷新后重试");
        }
        RescheduleHcSlotBookingResponse response = new RescheduleHcSlotBookingResponse();
        response.setAppointmentId(appointment.getId());
        response.setNewDaySlotId(newSlot.getId());
        // Asm：返回改约结果。
        return response;
    }

    /**
     * 解析下单合同类型：无合同强制 WALK_IN；有合同须传 contractTypeCode。
     *
     * @param request 创建预约请求
     * @return 进池合同类型编码
     */
    private String resolveContractTypeCode(CreateHcSlotBookingRequest request) {
        if (request.getContractId() == null) {
            return PeisContractTypeCodes.WALK_IN;
        }
        if (!StringUtils.hasText(request.getContractTypeCode())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "有合同时 contractTypeCode 不能为空");
        }
        return request.getContractTypeCode().trim();
    }

    /**
     * 断言时段配额可约（机构、类型、校区、日计划、开放与余量）。
     *
     * @param request 创建预约请求
     * @param daySlot 时段配额
     * @param contractTypeCode 合同类型编码
     */
    private void assertDaySlotBookable(CreateHcSlotBookingRequest request, HcDaySlot daySlot, String contractTypeCode) {
        if (!request.getOrganizationId().equals(daySlot.getOrganizationId())) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "时段配额与机构不匹配");
        }
        if (!contractTypeCode.equals(daySlot.getContractTypeCode())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "合同类型与时段配额不匹配");
        }
        HcDayPlan plan = slotCalendarQueryService.loadDayPlan(daySlot.getOrganizationId(), daySlot.getPlanDate());
        if (Boolean.TRUE.equals(plan.getRestFlag())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "该日为休息日不可预约");
        }
        if (!this.isContractTypeAllowed(plan.getId(), contractTypeCode)) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "该日未开放此合同类型");
        }
        if (!this.isBookableSlot(daySlot, contractTypeCode, null)) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "时段不可约");
        }
    }

    /**
     * 判断时段是否满足可约条件。
     *
     * @param slot 时段配额
     * @param contractTypeCode 合同类型
     * @param campusFilter 校区过滤（null 表示不过滤）
     * @return 可约时 true
     */
    private boolean isBookableSlot(HcDaySlot slot, String contractTypeCode, String campusFilter) {
        if (!contractTypeCode.equals(slot.getContractTypeCode())) {
            return false;
        }
        if (campusFilter != null) {
            if (slot.getCampusCode() == null) {
                return false;
            }
            if (!campusFilter.equals(slot.getCampusCode())) {
                return false;
            }
        }
        if (!Boolean.TRUE.equals(slot.getOpenFlag())) {
            return false;
        }
        if (slot.getCapacity() == null || slot.getCapacity() <= 0) {
            return false;
        }
        if (slot.remainingCount() <= 0) {
            return false;
        }
        return true;
    }

    /**
     * 判断日计划是否开放指定合同类型。
     *
     * @param dayPlanId 日计划主键
     * @param contractTypeCode 合同类型编码
     * @return 开放时 true
     */
    private boolean isContractTypeAllowed(Long dayPlanId, String contractTypeCode) {
        List<HcDayType> types = slotCalendarQueryService.listDayTypesByDayPlanId(dayPlanId);
        if (types.isEmpty()) {
            return true;
        }
        for (HcDayType type : types) {
            if (contractTypeCode.equals(type.getContractTypeCode())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 汇总某日在指定类型/校区下的可约余量。
     *
     * @param plan 日计划
     * @param allSlots 全部时段
     * @param planDate 计划日期
     * @param contractTypeCode 合同类型
     * @param campusFilter 校区过滤
     * @return 可约余量合计
     */
    private int sumBookableRemaining(
        HcDayPlan plan,
        List<HcDaySlot> allSlots,
        LocalDate planDate,
        String contractTypeCode,
        String campusFilter) {
        if (plan == null) {
            return 0;
        }
        if (Boolean.TRUE.equals(plan.getRestFlag())) {
            return 0;
        }
        if (!this.isContractTypeAllowed(plan.getId(), contractTypeCode)) {
            return 0;
        }
        int sum = 0;
        for (HcDaySlot slot : allSlots) {
            if (!planDate.equals(slot.getPlanDate())) {
                continue;
            }
            if (!this.isBookableSlot(slot, contractTypeCode, campusFilter)) {
                continue;
            }
            sum = sum + slot.remainingCount();
        }
        return sum;
    }

    /**
     * 从日计划列表中查找指定日期。
     *
     * @param plans 日计划列表
     * @param planDate 计划日期
     * @return 日计划或 null
     */
    private HcDayPlan findPlanForDate(List<HcDayPlan> plans, LocalDate planDate) {
        for (HcDayPlan plan : plans) {
            if (planDate.equals(plan.getPlanDate())) {
                return plan;
            }
        }
        return null;
    }

    /**
     * 构建日期列表。
     *
     * @param fromDate 起始日期
     * @param toDate 结束日期
     * @return 日期列表
     */
    private List<LocalDate> buildDateList(LocalDate fromDate, LocalDate toDate) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate cursor = fromDate;
        while (!cursor.isAfter(toDate)) {
            dates.add(cursor);
            cursor = cursor.plusDays(1);
        }
        return dates;
    }

    /**
     * 读路径解析校区过滤值。
     *
     * @param campusCode 请求校区编码
     * @return 校区过滤值或 null
     */
    private String resolveCampusCodeForRead(String campusCode) {
        if (!bookingCampusSupport.isCampusModuleEnabled()) {
            return null;
        }
        if (!StringUtils.hasText(campusCode)) {
            return null;
        }
        return campusCode.trim();
    }

    /**
     * 校验并加载体检预约详情（读模型）。
     *
     * @param appointmentId 预约主键
     */
    private void requireHealthCheckupAppointment(Long appointmentId) {
        var booking = appointmentQueryService.loadOutpatientBookingById(appointmentId);
        if (!HEALTH_CHECKUP_BOOKING_KIND.equals(booking.getAppointmentKind())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "非体检预约不允许此操作");
        }
    }

    /**
     * 校验并加载体检预约实体。
     *
     * @param appointmentId 预约主键
     * @return 预约实体
     */
    private Appointment requireHealthCheckupAppointmentEntity(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "预约不存在"));
        if (Boolean.TRUE.equals(appointment.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "预约已取消");
        }
        if (!HEALTH_CHECKUP_BOOKING_KIND.equals(appointment.getAppointmentKind())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "非体检预约不允许此操作");
        }
        return appointment;
    }
}
