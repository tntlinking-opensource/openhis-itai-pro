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
import com.openhis.whale.booking.application.support.BookingOperatorResolver;
import com.openhis.whale.booking.application.support.BookingPagedResultBuilder;
import com.openhis.whale.booking.contracts.port.BookingServiceRequestPort;
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
import com.openhis.whale.booking.domain.share.AppointmentKindConstants;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 渠道配置应用服务。
 *
 * <p>接入：POST /api/app/channelBooking/{methodName}</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class ChannelBookingAppService {

    private final ChannelQueryService channelQueryService;
    private final ChannelDomainService channelDomainService;
    private final AppointmentDomainService appointmentDomainService;
    private final AppointmentQueryService appointmentQueryService;
    private final SlotDomainService slotDomainService;
    private final SlotQueryService slotQueryService;
    private final HealthCheckupQueryService healthCheckupQueryService;
    private final HealthCheckupDomainService healthCheckupDomainService;
    private final BookingServiceRequestPort bookingServiceRequestPort;
    private final ChannelBookingMapper channelBookingMapper;
    private final BookingOperatorResolver bookingOperatorResolver;

    public PageChannelConfigsResponse pageChannelConfigs(@Valid PageChannelConfigsRequest request) {
        Page<ChannelConfig> page = channelQueryService.pageByOrganization(
            request.getOrganizationId(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<ChannelConfigDto> rows = new ArrayList<>();
        for (ChannelConfig config : page.getContent()) {
            rows.add(channelBookingMapper.toChannelConfigDto(config));
        }
        rows = channelBookingMapper.filterByKeyword(rows, request.getKeyword());
        return BookingPagedResultBuilder.build(new PageChannelConfigsResponse(), rows, page);
    }

    public ChannelConfigDto loadChannelConfig(@Valid LoadChannelConfigRequest request) {
        ChannelConfig config = channelQueryService.loadById(request.getId());
        if (config == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "渠道配置不存在");
        }
        return channelBookingMapper.toChannelConfigDto(config);
    }

    @Transactional
    public SaveChannelConfigResponse saveChannelConfig(@Valid SaveChannelConfigRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        ChannelConfig saved = channelDomainService.save(
            channelBookingMapper.toChannelConfigEntity(request), operatorId);
        SaveChannelConfigResponse response = new SaveChannelConfigResponse();
        response.setId(saved.getId());
        return response;
    }

    @Transactional
    public void deleteChannelConfig(@Valid DeleteChannelConfigRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        channelDomainService.softDelete(request.getId(), operatorId);
    }

    @Transactional
    public ChannelCreateBookingResponse channelCreateBooking(@Valid ChannelCreateBookingRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        this.requireEnabledChannel(request.getOrganizationId(), request.getChannelCode());
        SlotDetailModel slot = slotQueryService.loadSlotById(request.getSlotId());
        if (!request.getOrganizationId().equals(slot.getOrganizationId())) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "号源与机构不匹配");
        }
        slotDomainService.allocateBooking(
            channelBookingMapper.toAllocateBookingCommand(request.getSlotId(), operatorId));
        Appointment appointment = appointmentDomainService.create(
            channelBookingMapper.toChannelCreateAppointmentCommand(request, slot, operatorId));
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
            bookingServiceRequestPort.markServiceRequestBooked(request.getServiceRequestId(), appointment.getId());
        }
        return channelBookingMapper.toChannelCreateBookingResponse(appointment);
    }

    @Transactional
    public void channelCancelBooking(@Valid ChannelCancelBookingRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        this.requireEnabledChannel(request.getOrganizationId(), request.getChannelCode());
        AppointmentBookingDetailModel booking = appointmentQueryService.loadOutpatientBookingById(request.getAppointmentId());
        appointmentDomainService.cancelAppointment(
            channelBookingMapper.toChannelCancelAppointmentCommand(request, operatorId));
        if (booking.getSlotId() != null) {
            slotDomainService.releaseBooking(
                channelBookingMapper.toReleaseBookingCommand(booking.getSlotId(), operatorId));
        }
    }

    public ChannelQueryBookingResponse channelQueryBooking(@Valid ChannelQueryBookingRequest request) {
        this.requireEnabledChannel(request.getOrganizationId(), request.getChannelCode());
        if (StringUtils.hasText(request.getAppointmentBusNo())) {
            ActiveOutpatientBookingLookupQuery query = new ActiveOutpatientBookingLookupQuery();
            query.setAppointmentBusNo(request.getAppointmentBusNo());
            query.setRegistrationDate(LocalDate.now());
            Optional<AppointmentBookingDetailModel> found =
                appointmentQueryService.findActiveOutpatientBookingForRegister(query);
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
