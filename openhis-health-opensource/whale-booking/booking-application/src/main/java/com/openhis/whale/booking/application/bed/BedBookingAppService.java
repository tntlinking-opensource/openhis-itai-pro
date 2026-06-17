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
import com.openhis.whale.booking.application.support.BookingOperatorResolver;
import com.openhis.whale.booking.application.support.BookingPagedResultBuilder;
import com.openhis.whale.booking.contracts.dto.BedLocationSummaryDto;
import com.openhis.whale.booking.contracts.port.BookingBedPort;
import com.openhis.whale.booking.contracts.port.BookingLocationPort;
import com.openhis.whale.booking.contracts.port.BookingPatientPort;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 床位预约应用服务。
 *
 * <p>接入：POST /api/app/bedBooking/{methodName}</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class BedBookingAppService {

    private final BookingLocationPort bookingLocationPort;
    private final BookingBedPort bookingBedPort;
    private final BookingPatientPort bookingPatientPort;
    private final BedReservationQueryService bedReservationQueryService;
    private final BedReservationDomainService bedReservationDomainService;
    private final AppointmentDomainService appointmentDomainService;
    private final AppointmentQueryService appointmentQueryService;
    private final BedBookingMapper bedBookingMapper;
    private final BookingOperatorResolver bookingOperatorResolver;

    /**
     * 床位预约台初始化（8.1）。
     */
    public LoadBedBookingFormInitResponse loadBedBookingFormInit(LoadBedBookingFormInitRequest request) {
        Long organizationId = request == null ? null : request.getOrganizationId();
        return bedBookingMapper.toLoadBedBookingFormInitResponse(
            bookingLocationPort.listWardsByOrganization(organizationId));
    }

    /**
     * 加载机构下病区树。
     */
    public LoadWardTreeResponse loadWardTree(@Valid LoadWardTreeRequest request) {
        return bedBookingMapper.toLoadWardTreeResponse(
            bookingLocationPort.listWardsByOrganization(request.getOrganizationId()));
    }

    /**
     * 查询病区床位矩阵日历。
     */
    public PageBedMatrixResponse pageBedMatrix(@Valid PageBedMatrixRequest request) {
        if (request.getRangeEnd().isBefore(request.getRangeStart())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "日期区间非法");
        }
        List<BedLocationSummaryDto> beds = bookingLocationPort.listBedsUnderWard(request.getWardLocationId());
        List<String> dates = bedBookingMapper.buildDateHeaders(request.getRangeStart(), request.getRangeEnd());
        List<BedReservationExt> reservations = bedReservationQueryService.listByWardAndDateRange(
            request.getWardLocationId(), request.getRangeStart(), request.getRangeEnd());
        Map<Long, String> patientNames = bookingPatientPort.findPatientNamesByIds(
            bedBookingMapper.collectPatientIds(reservations));
        BedBookingMapper.PageBedMatrixRows matrixRows = bedBookingMapper.buildBedMatrixRows(
            beds, dates, reservations, patientNames, LocalDate.now());
        PageBedMatrixResponse response = new PageBedMatrixResponse();
        response.setDates(dates);
        response.setRows(matrixRows.getRows());
        return response;
    }

    /**
     * 预留床位：写 Appointment + 扩展表 + Location 运营状态。
     */
    @Transactional
    public ReserveBedResponse reserveBed(@Valid ReserveBedRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        if (appointmentQueryService.findActiveBedBookingByBedAndDate(
            request.getBedLocationId(), request.getIntendedAdmissionDate()).isPresent()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "该床位当日已有有效预留");
        }
        Long wardLocationId = bookingLocationPort.resolveWardLocationIdByBedLocationId(request.getBedLocationId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "无法解析床位所属病区"));
        List<BedLocationSummaryDto> beds = bookingLocationPort.listBedsUnderWard(wardLocationId);
        BedLocationSummaryDto targetBed = this.findBed(beds, request.getBedLocationId());
        if (targetBed == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "床位不存在或未启用");
        }
        bookingBedPort.reserveBed(bedBookingMapper.toReserveBedCommand(
            request.getBedLocationId(), operatorId, request.getCommentText()));
        Appointment appointment = appointmentDomainService.create(
            bedBookingMapper.toCreateBedAppointmentCommand(request, operatorId));
        bedReservationDomainService.save(
            bedBookingMapper.toBedReservationExt(
                request, appointment.getId(), wardLocationId, operatorId),
            operatorId);
        return bedBookingMapper.toReserveBedResponse(appointment);
    }

    /**
     * 取消床位预留。
     */
    @Transactional
    public void cancelBedReservation(@Valid CancelBedReservationRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        AppointmentBookingDetailModel booking = appointmentQueryService.loadBedBookingById(request.getAppointmentId());
        BedReservationExt ext = bedReservationQueryService.findByAppointmentId(request.getAppointmentId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "床位预留扩展不存在"));
        appointmentDomainService.cancelAppointment(
            bedBookingMapper.toCancelAppointmentCommand(request, operatorId));
        if (booking.getDestinationLocationId() != null) {
            bookingBedPort.releaseBedReservation(
                bedBookingMapper.toReleaseBedReservationCommand(booking.getDestinationLocationId(), operatorId));
        }
        bedReservationDomainService.softDelete(ext.getId(), operatorId);
    }

    /**
     * 床位预留列表分页。
     */
    public PageBedReservationsResponse pageBedReservations(@Valid PageBedReservationsRequest request) {
        Page<BedReservationExt> page = bedReservationQueryService.pageReservations(
            request.getOrganizationId(),
            request.getWardLocationId(),
            request.getDateFrom(),
            request.getDateTo(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        Map<Long, String> patientNames = bookingPatientPort.findPatientNamesByIds(
            bedBookingMapper.collectPatientIds(page.getContent()));
        List<BedLocationSummaryDto> beds = request.getWardLocationId() != null
            ? bookingLocationPort.listBedsUnderWard(request.getWardLocationId())
            : List.of();
        Map<Long, String> bedNames = bedBookingMapper.indexBedNames(beds);
        List<BedReservationListItemDto> rows = new ArrayList<>();
        for (BedReservationExt ext : page.getContent()) {
            AppointmentBookingDetailModel appointment = appointmentQueryService.loadBedBookingById(ext.getAppointmentId());
            if (!request.getOrganizationId().equals(appointment.getOrganizationId())) {
                continue;
            }
            rows.add(bedBookingMapper.toBedReservationListItemDto(ext, appointment, patientNames, bedNames));
        }
        return BookingPagedResultBuilder.build(new PageBedReservationsResponse(), rows, page);
    }

    /**
     * 入院确认（8.5）：关闭床位预约并释放 RESERVED，由住院登记后续占床。
     */
    @Transactional
    public ConfirmAdmissionResponse confirmAdmission(@Valid ConfirmAdmissionRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        AppointmentBookingDetailModel booking = appointmentQueryService.loadBedBookingById(request.getAppointmentId());
        BedReservationExt ext = bedReservationQueryService.findByAppointmentId(request.getAppointmentId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "床位预留扩展不存在"));
        appointmentDomainService.cancelAppointment(
            bedBookingMapper.toConfirmAdmissionCancelCommand(request, operatorId));
        if (booking.getDestinationLocationId() != null) {
            bookingBedPort.releaseBedReservation(
                bedBookingMapper.toReleaseBedReservationCommand(booking.getDestinationLocationId(), operatorId));
        }
        bedReservationDomainService.softDelete(ext.getId(), operatorId);
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
