/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.channel;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.application.channel.mapper.ChannelBookingMapper;
import com.openhis.whale.booking.application.channel.request.ChannelCancelBookingRequest;
import com.openhis.whale.booking.application.channel.request.ChannelCreateBookingRequest;
import com.openhis.whale.booking.application.channel.request.ChannelQueryBookingRequest;
import com.openhis.whale.booking.application.channel.request.DeleteChannelConfigRequest;
import com.openhis.whale.booking.application.channel.request.LoadChannelConfigRequest;
import com.openhis.whale.booking.application.channel.request.PageChannelConfigsRequest;
import com.openhis.whale.booking.application.channel.request.SaveChannelConfigRequest;
import com.openhis.whale.booking.application.channel.response.ChannelConfigDto;
import com.openhis.whale.booking.application.channel.response.ChannelCreateBookingResponse;
import com.openhis.whale.booking.application.channel.response.ChannelQueryBookingResponse;
import com.openhis.whale.booking.application.channel.response.PageChannelConfigsResponse;
import com.openhis.whale.booking.application.channel.response.SaveChannelConfigResponse;
import com.openhis.whale.booking.application.share.BookingOperatorResolver;
import com.openhis.whale.booking.application.share.BookingPagedResultBuilder;
import com.openhis.whale.booking.application.share.OutpatientBookingWriteSupport;
import com.openhis.whale.booking.application.share.OutpatientWaitlistPromoteSupport;
import com.openhis.whale.booking.domain.medtech.service.BookingServiceRequestCommandService;
import com.openhis.whale.booking.domain.appointment.entity.Appointment;
import com.openhis.whale.booking.domain.appointment.queries.ActiveOutpatientBookingLookupQuery;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentBookingDetailModel;
import com.openhis.whale.booking.domain.appointment.service.AppointmentDomainService;
import com.openhis.whale.booking.domain.appointment.service.AppointmentQueryService;
import com.openhis.whale.booking.domain.channel.entity.ChannelConfig;
import com.openhis.whale.booking.domain.channel.service.ChannelDomainService;
import com.openhis.whale.booking.domain.channel.service.ChannelQueryService;
import com.openhis.whale.booking.domain.healthcheckup.entity.HealthCheckPackage;
import com.openhis.whale.booking.domain.healthcheckup.service.HealthCheckupDomainService;
import com.openhis.whale.booking.domain.healthcheckup.service.HealthCheckupQueryService;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleCalendarItemModel;
import com.openhis.whale.booking.domain.schedule.service.ScheduleQueryService;
import com.openhis.whale.booking.domain.share.AppointmentKindConstants;
import com.openhis.whale.booking.domain.share.BookingDateSupport;
import com.openhis.whale.booking.domain.slot.queries.model.SlotDetailModel;
import com.openhis.whale.booking.domain.slot.service.SlotDomainService;
import com.openhis.whale.booking.domain.slot.service.SlotQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 渠道预约与渠道配置应用服务（C 线）。
 *
 * <p>职责边界：第三方渠道配置 CRUD、渠道侧创建/取消/查询预约；占号与写 Appointment 经领域服务。</p>
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/channelBooking/{methodName}}。</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class ChannelBookingAppService {

    /** 渠道配置读侧。 */
    private final ChannelQueryService channelQueryService;
    /** 渠道配置写侧。 */
    private final ChannelDomainService channelDomainService;
    /** 预约写侧：创建、取消。 */
    private final AppointmentDomainService appointmentDomainService;
    /** 预约读侧：按单号/患者查询。 */
    private final AppointmentQueryService appointmentQueryService;
    /** 号源写侧：allocate/release。 */
    private final SlotDomainService slotDomainService;
    /** 号源读侧：校验机构归属。 */
    private final SlotQueryService slotQueryService;
    /** 体检套餐读侧（渠道体检预约扩展）。 */
    private final HealthCheckupQueryService healthCheckupQueryService;
    /** 体检扩展写侧。 */
    private final HealthCheckupDomainService healthCheckupDomainService;
    /** 医嘱预约回写（医技渠道预约）。 */
    private final BookingServiceRequestCommandService bookingServiceRequestCommandService;
    /** Request/Command/DTO 映射。 */
    private final ChannelBookingMapper channelBookingMapper;
    /** 写路径操作人。 */
    private final BookingOperatorResolver bookingOperatorResolver;
    /** 日程读侧（防重解析 healthcareServiceId）。 */
    private final ScheduleQueryService scheduleQueryService;
    /** 门诊预约占号 + 建单。 */
    private final OutpatientBookingWriteSupport outpatientBookingWriteSupport;
    /** 候补自动提升。 */
    private final OutpatientWaitlistPromoteSupport outpatientWaitlistPromoteSupport;

    /**
     * 渠道配置分页列表。
     *
     * @param request 机构、关键字与分页参数
     * @return 渠道配置分页结果
     */
    public PageChannelConfigsResponse pageChannelConfigs(@Valid PageChannelConfigsRequest request) {
        // Pre：按机构分页加载渠道配置。
        Page<ChannelConfig> page = channelQueryService.pageByOrganization(
            request.getOrganizationId(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<ChannelConfigDto> rows = new ArrayList<>();
        for (ChannelConfig config : page.getContent()) {
            rows.add(channelBookingMapper.toChannelConfigDto(config));
        }
        rows = channelBookingMapper.filterByKeyword(rows, request.getKeyword());
        // Asm：映射 DTO 并包装分页结果。
        return BookingPagedResultBuilder.build(new PageChannelConfigsResponse(), rows, page);
    }

    /**
     * 加载渠道配置详情。
     *
     * @param request 配置主键
     * @return 渠道配置 DTO
     */
    public ChannelConfigDto loadChannelConfig(@Valid LoadChannelConfigRequest request) {
        // Pre：按主键加载渠道配置。
        ChannelConfig config = channelQueryService.loadById(request.getId());
        if (config == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "渠道配置不存在");
        }
        // Asm：映射渠道配置 DTO。
        return channelBookingMapper.toChannelConfigDto(config);
    }

    /**
     * 保存渠道配置。
     *
     * @param request 渠道配置字段
     * @return 保存后的主键
     */
    public SaveChannelConfigResponse saveChannelConfig(@Valid SaveChannelConfigRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：持久化渠道配置。
        ChannelConfig saved = channelDomainService.save(
            channelBookingMapper.toChannelConfigEntity(request), operatorId);
        // Asm：返回主键。
        SaveChannelConfigResponse response = new SaveChannelConfigResponse();
        response.setId(saved.getId());
        return response;
    }

    /**
     * 删除渠道配置（软删）。
     *
     * @param request 配置主键
     */
    public void deleteChannelConfig(@Valid DeleteChannelConfigRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：软删渠道配置。
        channelDomainService.softDelete(request.getId(), operatorId);
    }

    /**
     * 渠道创建预约：校验渠道 → 占号 → 写 Appointment（含体检/医技扩展）。
     *
     * @param request 渠道、患者、号源及预约类型
     * @return 新预约主键与业务号
     */
    public ChannelCreateBookingResponse channelCreateBooking(@Valid ChannelCreateBookingRequest request) {
        // Gate：写路径须登录且渠道已启用。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        this.requireEnabledChannel(request.getOrganizationId(), request.getChannelCode());
        // Pre：校验号源机构归属。
        SlotDetailModel slot = slotQueryService.loadSlotById(request.getSlotId());
        if (!request.getOrganizationId().equals(slot.getOrganizationId())) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "号源与机构不匹配");
        }
        ScheduleCalendarItemModel scheduleMeta = scheduleQueryService.loadCalendarItemById(slot.getScheduleId());
        Long healthcareServiceId = scheduleMeta.getHealthcareServiceId();
        LocalDate bookingDate = BookingDateSupport.resolveBookingDate(slot.getStartTime());
        if (AppointmentKindConstants.OUTPATIENT_BOOKING.equals(request.getAppointmentKind())) {
            outpatientBookingWriteSupport.assertNoDuplicateOutpatientBooking(
                request.getPatientId(), bookingDate, healthcareServiceId, null);
        }
        // Cmd：占号并创建预约。
        Appointment appointment;
        if (AppointmentKindConstants.OUTPATIENT_BOOKING.equals(request.getAppointmentKind())) {
            appointment = outpatientBookingWriteSupport.allocateAndCreate(
                channelBookingMapper.toChannelCreateAppointmentCommand(
                    request, slot, healthcareServiceId, bookingDate, operatorId),
                request.getSlotId(),
                operatorId);
        } else {
            slotDomainService.allocateBooking(
                channelBookingMapper.toAllocateBookingCommand(request.getSlotId(), operatorId));
            appointment = appointmentDomainService.create(
                channelBookingMapper.toChannelCreateAppointmentCommand(
                    request, slot, healthcareServiceId, bookingDate, operatorId));
        }
        // Cmd：按预约类型写扩展（体检套餐 / 医嘱已预约）。
        if (AppointmentKindConstants.HEALTH_CHECKUP_BOOKING.equals(request.getAppointmentKind())) {
            if (request.getPackageId() == null) {
                throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "体检预约 packageId 不能为空");
            }
            HealthCheckPackage pkg = healthCheckupQueryService.loadPackageById(request.getPackageId());
            healthCheckupDomainService.saveBookingExt(
                channelBookingMapper.toHealthCheckBookingExt(appointment.getId(), pkg), operatorId);
        }
        if (request.getServiceRequestId() != null
            && AppointmentKindConstants.DIAGNOSTIC_BOOKING.equals(request.getAppointmentKind())) {
            bookingServiceRequestCommandService.markServiceRequestBooked(request.getServiceRequestId(), appointment.getId());
        }
        // Asm：映射创建响应。
        return channelBookingMapper.toChannelCreateBookingResponse(appointment);
    }

    /**
     * 渠道取消预约：取消 Appointment 并释放号源。
     *
     * @param request 渠道、预约主键与取消原因
     */
    public void channelCancelBooking(@Valid ChannelCancelBookingRequest request) {
        // Gate：写路径须登录且渠道已启用。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        this.requireEnabledChannel(request.getOrganizationId(), request.getChannelCode());
        // Pre：加载预约以获取 slotId。
        AppointmentBookingDetailModel booking = appointmentQueryService.loadOutpatientBookingById(request.getAppointmentId());
        // Cmd：取消预约。
        appointmentDomainService.cancelAppointment(
            channelBookingMapper.toChannelCancelAppointmentCommand(request, operatorId));
        // Cmd：释放号源容量。
        if (booking.getSlotId() != null) {
            slotDomainService.releaseBooking(
                channelBookingMapper.toReleaseBookingCommand(booking.getSlotId(), operatorId));
            outpatientWaitlistPromoteSupport.tryPromoteWaitlist(booking.getSlotId(), operatorId);
        }
    }

    /**
     * 渠道查询当日有效预约（按业务号或患者）。
     *
     * @param request 渠道、patientId 或 appointmentBusNo（二选一）
     * @return 预约摘要；未找到时返回 null
     */
    public ChannelQueryBookingResponse channelQueryBooking(@Valid ChannelQueryBookingRequest request) {
        // Gate：渠道须已配置且启用。
        this.requireEnabledChannel(request.getOrganizationId(), request.getChannelCode());
        // Pre：按业务号或患者检索当日有效预约。
        if (StringUtils.hasText(request.getAppointmentBusNo())) {
            ActiveOutpatientBookingLookupQuery query = new ActiveOutpatientBookingLookupQuery();
            query.setAppointmentBusNo(request.getAppointmentBusNo());
            query.setRegistrationDate(LocalDate.now());
            Optional<AppointmentBookingDetailModel> found =
                appointmentQueryService.findActiveOutpatientBookingForRegister(query);
            // Asm：映射预约摘要；未找到时返回 null。
            return found.map(channelBookingMapper::toChannelQueryBookingResponse).orElse(null);
        }
        if (request.getPatientId() != null) {
            ActiveOutpatientBookingLookupQuery query = new ActiveOutpatientBookingLookupQuery();
            query.setPatientId(request.getPatientId());
            query.setRegistrationDate(LocalDate.now());
            Optional<AppointmentBookingDetailModel> found =
                appointmentQueryService.findActiveOutpatientBookingForRegister(query);
            return found.map(channelBookingMapper::toChannelQueryBookingResponse).orElse(null);
        }
        throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "patientId 与 appointmentBusNo 至少传一个");
    }

    private ChannelConfig requireEnabledChannel(Long organizationId, String channelCode) {
        ChannelConfig config = channelQueryService.findByOrganizationAndCode(organizationId, channelCode)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "渠道未配置"));
        if (Boolean.FALSE.equals(config.getEnabledFlag())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "渠道未启用");
        }
        return config;
    }
}
