/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.bed;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.application.bed.mapper.BedBookingMapper;
import com.openhis.whale.booking.application.bed.request.CancelBedReservationRequest;
import com.openhis.whale.booking.application.bed.request.ConfirmAdmissionRequest;
import com.openhis.whale.booking.application.bed.request.LoadBedBookingFormInitRequest;
import com.openhis.whale.booking.application.bed.request.LoadWardTreeRequest;
import com.openhis.whale.booking.application.bed.request.PageBedMatrixRequest;
import com.openhis.whale.booking.application.bed.request.PageBedReservationsRequest;
import com.openhis.whale.booking.application.bed.request.ReserveBedRequest;
import com.openhis.whale.booking.application.bed.response.LoadBedBookingFormInitResponse;
import com.openhis.whale.booking.application.bed.response.LoadWardTreeResponse;
import com.openhis.whale.booking.application.bed.response.ConfirmAdmissionResponse;
import com.openhis.whale.booking.application.bed.response.PageBedMatrixResponse;
import com.openhis.whale.booking.application.bed.response.PageBedReservationsResponse;
import com.openhis.whale.booking.application.bed.response.ReserveBedResponse;
import com.openhis.whale.booking.application.bed.response.BedReservationListItemDto;
import com.openhis.whale.booking.application.share.BookingOperatorResolver;
import com.openhis.whale.booking.application.share.BookingPagedResultBuilder;
import com.openhis.whale.booking.domain.masterdata.result.BedLocationSummaryDto;
import com.openhis.whale.booking.domain.bed.service.BookingBedReservationCommandService;
import com.openhis.whale.booking.domain.masterdata.service.BookingLocationQueryService;
import com.openhis.whale.booking.domain.masterdata.service.BookingPatientQueryService;
import com.openhis.whale.booking.domain.appointment.entity.Appointment;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentBookingDetailModel;
import com.openhis.whale.booking.domain.appointment.service.AppointmentDomainService;
import com.openhis.whale.booking.domain.appointment.service.AppointmentQueryService;
import com.openhis.whale.booking.domain.bed.entity.BedReservationExt;
import com.openhis.whale.booking.domain.bed.service.BedReservationDomainService;
import com.openhis.whale.booking.domain.bed.service.BedReservationQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 床位预约应用服务（8.x）。
 *
 * <p>职责边界：病区床位矩阵、预留/取消/入院确认；写 Location 运营状态经 {@link BookingBedReservationCommandService}。</p>
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/bedBooking/{methodName}}。</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class BedBookingAppService {

    /** 病区/床位主数据。 */
    private final BookingLocationQueryService bookingLocationQueryService;
    /** 床位运营状态（预留/释放）。 */
    private final BookingBedReservationCommandService bookingBedReservationCommandService;
    /** 患者主数据。 */
    private final BookingPatientQueryService bookingPatientQueryService;
    /** 床位预留扩展读侧。 */
    private final BedReservationQueryService bedReservationQueryService;
    /** 床位预留扩展写侧。 */
    private final BedReservationDomainService bedReservationDomainService;
    /** 预约写侧：创建、取消。 */
    private final AppointmentDomainService appointmentDomainService;
    /** 预约读侧：详情、重复校验。 */
    private final AppointmentQueryService appointmentQueryService;
    /** Request/Command/DTO 映射。 */
    private final BedBookingMapper bedBookingMapper;
    /** 写路径操作人。 */
    private final BookingOperatorResolver bookingOperatorResolver;

    /**
     * 床位预约台初始化（8.1）。
     */
    public LoadBedBookingFormInitResponse loadBedBookingFormInit(LoadBedBookingFormInitRequest request) {
        // Pre：解析机构主键并加载病区主数据。
        Long organizationId;
        if (request == null) {
            organizationId = null;
        } else {
            organizationId = request.getOrganizationId();
        }
        // Asm：映射床位预约台初始化响应。
        return bedBookingMapper.toLoadBedBookingFormInitResponse(
            bookingLocationQueryService.listWardsByOrganization(organizationId));
    }

    /**
     * 加载机构下病区树。
     */
    public LoadWardTreeResponse loadWardTree(@Valid LoadWardTreeRequest request) {
        // Pre：按机构加载病区列表。
        // Asm：映射病区树响应。
        return bedBookingMapper.toLoadWardTreeResponse(
            bookingLocationQueryService.listWardsByOrganization(request.getOrganizationId()));
    }

    /**
     * 查询病区床位矩阵日历。
     */
    public PageBedMatrixResponse pageBedMatrix(@Valid PageBedMatrixRequest request) {
        // Pre：校验日期区间并加载床位、预留与患者姓名。
        if (request.getRangeEnd().isBefore(request.getRangeStart())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "日期区间非法");
        }
        List<BedLocationSummaryDto> beds = bookingLocationQueryService.listBedsUnderWard(request.getWardLocationId());
        List<String> dates = bedBookingMapper.buildDateHeaders(request.getRangeStart(), request.getRangeEnd());
        List<BedReservationExt> reservations = bedReservationQueryService.listByWardAndDateRange(
            request.getWardLocationId(), request.getRangeStart(), request.getRangeEnd());
        Map<Long, String> patientNames = bookingPatientQueryService.findPatientNamesByIds(
            bedBookingMapper.collectPatientIds(reservations));
        BedBookingMapper.PageBedMatrixRows matrixRows = bedBookingMapper.buildBedMatrixRows(
            beds, dates, reservations, patientNames, LocalDate.now());
        // Asm：组装床位矩阵日历响应。
        PageBedMatrixResponse response = new PageBedMatrixResponse();
        response.setDates(dates);
        response.setRows(matrixRows.getRows());
        return response;
    }

    /**
     * 预留床位：写 Appointment + 扩展表 + Location 运营状态。
     */
    public ReserveBedResponse reserveBed(@Valid ReserveBedRequest request) {
        // Gate：写路径须登录；同日同床不可重复预留。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        if (appointmentQueryService.findActiveBedBookingByBedAndDate(
            request.getBedLocationId(), request.getIntendedAdmissionDate()).isPresent()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "该床位当日已有有效预留");
        }
        // Pre：解析病区与目标床位。
        Long wardLocationId = bookingLocationQueryService.resolveWardLocationIdByBedLocationId(request.getBedLocationId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "无法解析床位所属病区"));
        List<BedLocationSummaryDto> beds = bookingLocationQueryService.listBedsUnderWard(wardLocationId);
        BedLocationSummaryDto targetBed = this.findBed(beds, request.getBedLocationId());
        if (targetBed == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "床位不存在或未启用");
        }
        // Cmd：标记床位 RESERVED、建预约与扩展表。
        bookingBedReservationCommandService.reserveBed(bedBookingMapper.toReserveBedCommand(
            request.getBedLocationId(), operatorId, request.getCommentText()));
        Appointment appointment = appointmentDomainService.create(
            bedBookingMapper.toCreateBedAppointmentCommand(request, operatorId));
        bedReservationDomainService.save(
            bedBookingMapper.toBedReservationExt(
                request, appointment.getId(), wardLocationId, operatorId),
            operatorId);
        // Asm：映射预留响应。
        return bedBookingMapper.toReserveBedResponse(appointment);
    }

    /**
     * 取消床位预留。
     */
    public void cancelBedReservation(@Valid CancelBedReservationRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：加载预约与扩展表。
        AppointmentBookingDetailModel booking = appointmentQueryService.loadBedBookingById(request.getAppointmentId());
        BedReservationExt ext = bedReservationQueryService.findByAppointmentId(request.getAppointmentId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "床位预留扩展不存在"));
        // Cmd：取消预约、释放床位 RESERVED、软删扩展。
        appointmentDomainService.cancelAppointment(
            bedBookingMapper.toCancelAppointmentCommand(request, operatorId));
        if (booking.getDestinationLocationId() != null) {
            bookingBedReservationCommandService.releaseBedReservation(
                bedBookingMapper.toReleaseBedReservationCommand(booking.getDestinationLocationId(), operatorId));
        }
        bedReservationDomainService.softDelete(ext.getId(), operatorId);
    }

    /**
     * 床位预留列表分页。
     */
    public PageBedReservationsResponse pageBedReservations(@Valid PageBedReservationsRequest request) {
        // Pre：分页加载预留扩展并批量解析患者与床位名称。
        Page<BedReservationExt> page = bedReservationQueryService.pageReservations(
            request.getOrganizationId(),
            request.getWardLocationId(),
            request.getDateFrom(),
            request.getDateTo(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        Map<Long, String> patientNames = bookingPatientQueryService.findPatientNamesByIds(
            bedBookingMapper.collectPatientIds(page.getContent()));
        List<BedLocationSummaryDto> beds;
        if (request.getWardLocationId() != null) {
            beds = bookingLocationQueryService.listBedsUnderWard(request.getWardLocationId());
        } else {
            beds = List.of();
        }
        Map<Long, String> bedNames = bedBookingMapper.indexBedNames(beds);
        List<BedReservationListItemDto> rows = new ArrayList<>();
        for (BedReservationExt ext : page.getContent()) {
            AppointmentBookingDetailModel appointment = appointmentQueryService.loadBedBookingById(ext.getAppointmentId());
            if (!request.getOrganizationId().equals(appointment.getOrganizationId())) {
                continue;
            }
            rows.add(bedBookingMapper.toBedReservationListItemDto(ext, appointment, patientNames, bedNames));
        }
        // Asm：映射列表项并包装分页结果。
        return BookingPagedResultBuilder.build(new PageBedReservationsResponse(), rows, page);
    }

    /**
     * 入院确认（8.5）：关闭床位预约并释放 RESERVED，由住院登记后续占床。
     */
    public ConfirmAdmissionResponse confirmAdmission(@Valid ConfirmAdmissionRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：加载预约与扩展表。
        AppointmentBookingDetailModel booking = appointmentQueryService.loadBedBookingById(request.getAppointmentId());
        BedReservationExt ext = bedReservationQueryService.findByAppointmentId(request.getAppointmentId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "床位预留扩展不存在"));
        // Cmd：关闭预约、释放 RESERVED、软删扩展（住院登记后续占床）。
        appointmentDomainService.cancelAppointment(
            bedBookingMapper.toConfirmAdmissionCancelCommand(request, operatorId));
        if (booking.getDestinationLocationId() != null) {
            bookingBedReservationCommandService.releaseBedReservation(
                bedBookingMapper.toReleaseBedReservationCommand(booking.getDestinationLocationId(), operatorId));
        }
        bedReservationDomainService.softDelete(ext.getId(), operatorId);
        // Asm：返回床位信息。
        ConfirmAdmissionResponse response = new ConfirmAdmissionResponse();
        response.setAppointmentId(request.getAppointmentId());
        response.setBedLocationId(ext.getLocationId());
        return response;
    }

    private BedLocationSummaryDto findBed(List<BedLocationSummaryDto> beds, Long bedLocationId) {
        if (beds == null || bedLocationId == null) {
            return null;
        }
        for (BedLocationSummaryDto bed : beds) {
            if (bedLocationId.equals(bed.getBedLocationId())) {
                return bed;
            }
        }
        return null;
    }
}
