package com.openhis.whale.booking.application.healthcheckup;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.application.healthcheckup.mapper.HealthCheckupBookingMapper;
import com.openhis.whale.booking.application.healthcheckup.request.BatchCreateHealthCheckupBookingRequest;
import com.openhis.whale.booking.application.healthcheckup.request.CheckInHealthCheckupRequest;
import com.openhis.whale.booking.application.healthcheckup.request.CreateIndividualHealthCheckupBookingRequest;
import com.openhis.whale.booking.application.healthcheckup.request.DeleteGroupCheckupBatchRequest;
import com.openhis.whale.booking.application.healthcheckup.request.DeleteGroupCheckupOrgRequest;
import com.openhis.whale.booking.application.healthcheckup.request.DeleteGroupCheckupRosterRequest;
import com.openhis.whale.booking.application.healthcheckup.request.DeleteHealthCheckPackageRequest;
import com.openhis.whale.booking.application.healthcheckup.request.ImportGroupCheckupRosterRequest;
import com.openhis.whale.booking.application.healthcheckup.request.PageGroupCheckupBatchesRequest;
import com.openhis.whale.booking.application.healthcheckup.request.PageGroupCheckupOrgsRequest;
import com.openhis.whale.booking.application.healthcheckup.request.PageGroupCheckupRostersRequest;
import com.openhis.whale.booking.application.healthcheckup.request.PageHealthCheckPackagesRequest;
import com.openhis.whale.booking.application.healthcheckup.request.SaveGroupCheckupBatchRequest;
import com.openhis.whale.booking.application.healthcheckup.request.SaveGroupCheckupOrgRequest;
import com.openhis.whale.booking.application.healthcheckup.request.SaveGroupCheckupRosterRequest;
import com.openhis.whale.booking.application.healthcheckup.request.SaveHealthCheckPackageRequest;
import com.openhis.whale.booking.application.healthcheckup.response.BatchCreateHealthCheckupBookingResponse;
import com.openhis.whale.booking.application.healthcheckup.response.CheckInHealthCheckupResponse;
import com.openhis.whale.booking.application.healthcheckup.response.CreateIndividualHealthCheckupBookingResponse;
import com.openhis.whale.booking.application.healthcheckup.response.GroupCheckupBatchDto;
import com.openhis.whale.booking.application.healthcheckup.response.GroupCheckupOrgDto;
import com.openhis.whale.booking.application.healthcheckup.response.GroupCheckupRosterDto;
import com.openhis.whale.booking.application.healthcheckup.response.HealthCheckPackageDto;
import com.openhis.whale.booking.application.healthcheckup.response.ImportGroupCheckupRosterResponse;
import com.openhis.whale.booking.application.healthcheckup.response.PageGroupCheckupBatchesResponse;
import com.openhis.whale.booking.application.healthcheckup.response.PageGroupCheckupOrgsResponse;
import com.openhis.whale.booking.application.healthcheckup.response.PageGroupCheckupRostersResponse;
import com.openhis.whale.booking.application.healthcheckup.response.PageHealthCheckPackagesResponse;
import com.openhis.whale.booking.application.healthcheckup.response.SaveGroupCheckupBatchResponse;
import com.openhis.whale.booking.application.healthcheckup.response.SaveGroupCheckupOrgResponse;
import com.openhis.whale.booking.application.healthcheckup.response.SaveGroupCheckupRosterResponse;
import com.openhis.whale.booking.application.healthcheckup.response.SaveHealthCheckPackageResponse;
import com.openhis.whale.booking.application.support.BookingOperatorResolver;
import com.openhis.whale.booking.application.support.BookingPagedResultBuilder;
import com.openhis.whale.booking.domain.appointment.entity.Appointment;
import com.openhis.whale.booking.domain.appointment.queries.ActiveOutpatientBookingLookupQuery;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentBookingDetailModel;
import com.openhis.whale.booking.domain.appointment.service.AppointmentDomainService;
import com.openhis.whale.booking.domain.appointment.service.AppointmentQueryService;
import com.openhis.whale.booking.domain.healthcheckup.entity.GroupCheckupBatch;
import com.openhis.whale.booking.domain.healthcheckup.entity.GroupCheckupOrg;
import com.openhis.whale.booking.domain.healthcheckup.entity.GroupCheckupRoster;
import com.openhis.whale.booking.domain.healthcheckup.entity.HealthCheckPackage;
import com.openhis.whale.booking.domain.healthcheckup.service.HealthCheckupDomainService;
import com.openhis.whale.booking.domain.healthcheckup.service.HealthCheckupQueryService;
import com.openhis.whale.booking.domain.share.AppointmentKindConstants;
import com.openhis.whale.booking.domain.slot.queries.FindBookableSlotsQuery;
import com.openhis.whale.booking.domain.slot.queries.model.BookableSlotModel;
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
 * 体检预约应用服务（H 线）。
 *
 * <p>接入：POST /api/app/healthCheckupBooking/{methodName}</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class HealthCheckupBookingAppService {

    private static final String HEALTH_CHECKUP_BOOKING_KIND = AppointmentKindConstants.HEALTH_CHECKUP_BOOKING;

    private final HealthCheckupQueryService healthCheckupQueryService;
    private final HealthCheckupDomainService healthCheckupDomainService;
    private final AppointmentDomainService appointmentDomainService;
    private final AppointmentQueryService appointmentQueryService;
    private final SlotQueryService slotQueryService;
    private final SlotDomainService slotDomainService;
    private final HealthCheckupBookingMapper healthCheckupBookingMapper;
    private final BookingOperatorResolver bookingOperatorResolver;

    public PageGroupCheckupOrgsResponse pageGroupCheckupOrgs(@Valid PageGroupCheckupOrgsRequest request) {
        Page<GroupCheckupOrg> page = healthCheckupQueryService.pageOrgs(
            request.getKeyword(), PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<GroupCheckupOrgDto> rows = new ArrayList<>();
        for (GroupCheckupOrg entity : page.getContent()) {
            rows.add(healthCheckupBookingMapper.toGroupCheckupOrgDto(entity));
        }
        return BookingPagedResultBuilder.build(new PageGroupCheckupOrgsResponse(), rows, page);
    }

    @Transactional
    public SaveGroupCheckupOrgResponse saveGroupCheckupOrg(@Valid SaveGroupCheckupOrgRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        GroupCheckupOrg saved = healthCheckupDomainService.saveOrg(
            healthCheckupBookingMapper.toGroupCheckupOrgEntity(request), operatorId);
        SaveGroupCheckupOrgResponse response = new SaveGroupCheckupOrgResponse();
        response.setId(saved.getId());
        return response;
    }

    @Transactional
    public void deleteGroupCheckupOrg(@Valid DeleteGroupCheckupOrgRequest request) {
        healthCheckupDomainService.deleteOrg(request.getId(), bookingOperatorResolver.requireOperatorId());
    }

    public PageGroupCheckupBatchesResponse pageGroupCheckupBatches(@Valid PageGroupCheckupBatchesRequest request) {
        Page<GroupCheckupBatch> page = healthCheckupQueryService.pageBatches(
            request.getOrgId(), PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<GroupCheckupBatchDto> rows = new ArrayList<>();
        for (GroupCheckupBatch entity : page.getContent()) {
            String orgName = null;
            if (entity.getOrgId() != null) {
                GroupCheckupOrg org = healthCheckupQueryService.loadOrgById(entity.getOrgId());
                orgName = org.getOrgName();
            }
            rows.add(healthCheckupBookingMapper.toGroupCheckupBatchDto(entity, orgName));
        }
        return BookingPagedResultBuilder.build(new PageGroupCheckupBatchesResponse(), rows, page);
    }

    @Transactional
    public SaveGroupCheckupBatchResponse saveGroupCheckupBatch(@Valid SaveGroupCheckupBatchRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        GroupCheckupBatch saved = healthCheckupDomainService.saveBatch(
            healthCheckupBookingMapper.toGroupCheckupBatchEntity(request), operatorId);
        SaveGroupCheckupBatchResponse response = new SaveGroupCheckupBatchResponse();
        response.setId(saved.getId());
        return response;
    }

    @Transactional
    public void deleteGroupCheckupBatch(@Valid DeleteGroupCheckupBatchRequest request) {
        healthCheckupDomainService.deleteBatch(request.getId(), bookingOperatorResolver.requireOperatorId());
    }

    public PageGroupCheckupRostersResponse pageGroupCheckupRosters(@Valid PageGroupCheckupRostersRequest request) {
        Page<GroupCheckupRoster> page = healthCheckupQueryService.pageRoster(
            request.getBatchId(), PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<GroupCheckupRosterDto> rows = new ArrayList<>();
        for (GroupCheckupRoster entity : page.getContent()) {
            rows.add(healthCheckupBookingMapper.toGroupCheckupRosterDto(entity));
        }
        return BookingPagedResultBuilder.build(new PageGroupCheckupRostersResponse(), rows, page);
    }

    @Transactional
    public SaveGroupCheckupRosterResponse saveGroupCheckupRoster(@Valid SaveGroupCheckupRosterRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        GroupCheckupRoster saved = healthCheckupDomainService.saveRoster(
            healthCheckupBookingMapper.toGroupCheckupRosterEntity(request), operatorId);
        SaveGroupCheckupRosterResponse response = new SaveGroupCheckupRosterResponse();
        response.setId(saved.getId());
        return response;
    }

    @Transactional
    public void deleteGroupCheckupRoster(@Valid DeleteGroupCheckupRosterRequest request) {
        healthCheckupDomainService.deleteRoster(request.getId(), bookingOperatorResolver.requireOperatorId());
    }

    @Transactional
    public ImportGroupCheckupRosterResponse importGroupCheckupRoster(@Valid ImportGroupCheckupRosterRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        healthCheckupQueryService.loadBatchById(request.getBatchId());
        int imported = 0;
        String[] lines = request.getCsvContent().split("\\r?\\n");
        for (String line : lines) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.contains("姓名") && trimmed.contains("身份证")) {
                continue;
            }
            String[] columns = trimmed.split(",");
            if (columns.length == 0 || !StringUtils.hasText(columns[0])) {
                continue;
            }
            healthCheckupDomainService.saveRoster(
                healthCheckupBookingMapper.toGroupCheckupRosterFromCsvRow(request.getBatchId(), columns), operatorId);
            imported++;
        }
        ImportGroupCheckupRosterResponse response = new ImportGroupCheckupRosterResponse();
        response.setImportedCount(imported);
        return response;
    }

    @Transactional
    public BatchCreateHealthCheckupBookingResponse batchCreateHealthCheckupBooking(
        @Valid BatchCreateHealthCheckupBookingRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        GroupCheckupBatch batch = healthCheckupQueryService.loadBatchById(request.getBatchId());
        if (batch.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "团检批次未关联日程");
        }
        GroupCheckupOrg org = healthCheckupQueryService.loadOrgById(batch.getOrgId());
        HealthCheckPackage pkg = batch.getPackageId() == null ? null : healthCheckupQueryService.loadPackageById(batch.getPackageId());
        List<GroupCheckupRoster> rosters = this.resolveRostersForBatchBooking(request);
        SlotDetailModel slot = this.resolveFirstBookableSlot(batch.getScheduleId(), batch.getBookingDate());
        Long organizationId = slot.getOrganizationId();
        if (organizationId == null && pkg != null) {
            organizationId = pkg.getOrganizationId();
        }
        if (organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "无法解析团检机构");
        }
        int booked = 0;
        for (GroupCheckupRoster roster : rosters) {
            if (!"PENDING".equals(roster.getBookingStatusCode()) || roster.getAppointmentId() != null) {
                continue;
            }
            slotDomainService.allocateBooking(
                healthCheckupBookingMapper.toAllocateBookingCommand(slot.getId(), operatorId));
            Appointment appointment = appointmentDomainService.create(
                healthCheckupBookingMapper.toGroupHealthCheckupAppointmentCommand(
                    batch, roster, slot, organizationId, operatorId));
            healthCheckupDomainService.saveBookingExt(
                healthCheckupBookingMapper.toHealthCheckBookingExt(
                    appointment.getId(), batch.getPackageId(), batch.getId(), pkg), operatorId);
            roster.setAppointmentId(appointment.getId());
            roster.setBookingStatusCode("BOOKED");
            healthCheckupDomainService.saveRoster(roster, operatorId);
            healthCheckupDomainService.incrementBatchBookedCount(batch.getId(), 1);
            booked++;
        }
        BatchCreateHealthCheckupBookingResponse response = new BatchCreateHealthCheckupBookingResponse();
        response.setBookedCount(booked);
        return response;
    }

    @Transactional
    public CheckInHealthCheckupResponse checkInHealthCheckup(@Valid CheckInHealthCheckupRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        AppointmentBookingDetailModel booking = this.resolveHealthCheckupBooking(request);
        CheckInHealthCheckupResponse response = new CheckInHealthCheckupResponse();
        if (booking.getDisplayOrder() != null) {
            response.setAppointmentId(booking.getId());
            response.setDisplayOrder(booking.getDisplayOrder());
            return response;
        }
        if (booking.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "体检到检签到需关联就诊");
        }
        Long destinationLocationId = request.getDestinationLocationId();
        if (destinationLocationId == null) {
            destinationLocationId = booking.getDestinationLocationId();
        }
        if (destinationLocationId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "到检签到 destinationLocationId 不能为空");
        }
        Appointment queueItem = appointmentDomainService.checkInToQueue(
            healthCheckupBookingMapper.toCheckInToQueueCommand(booking, destinationLocationId, operatorId));
        response.setAppointmentId(queueItem.getId());
        response.setDisplayOrder(queueItem.getDisplayOrder());
        return response;
    }

    public PageHealthCheckPackagesResponse pageHealthCheckPackages(@Valid PageHealthCheckPackagesRequest request) {
        Page<HealthCheckPackage> page = healthCheckupQueryService.pagePackages(
            request.getOrganizationId(), PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<HealthCheckPackageDto> rows = new ArrayList<>();
        for (HealthCheckPackage entity : page.getContent()) {
            rows.add(healthCheckupBookingMapper.toHealthCheckPackageDto(entity));
        }
        return BookingPagedResultBuilder.build(new PageHealthCheckPackagesResponse(), rows, page);
    }

    @Transactional
    public SaveHealthCheckPackageResponse saveHealthCheckPackage(@Valid SaveHealthCheckPackageRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        HealthCheckPackage saved = healthCheckupDomainService.savePackage(
            healthCheckupBookingMapper.toHealthCheckPackageEntity(request), operatorId);
        if (request.getItems() != null) {
            healthCheckupDomainService.replacePackageItems(
                saved.getId(),
                healthCheckupBookingMapper.toHealthCheckPackageItems(saved.getId(), request.getItems()),
                operatorId);
        }
        SaveHealthCheckPackageResponse response = new SaveHealthCheckPackageResponse();
        response.setId(saved.getId());
        return response;
    }

    @Transactional
    public void deleteHealthCheckPackage(@Valid DeleteHealthCheckPackageRequest request) {
        healthCheckupDomainService.deletePackage(request.getId(), bookingOperatorResolver.requireOperatorId());
    }

    @Transactional
    public CreateIndividualHealthCheckupBookingResponse createIndividualHealthCheckupBooking(
        @Valid CreateIndividualHealthCheckupBookingRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        HealthCheckPackage pkg = healthCheckupQueryService.loadPackageById(request.getPackageId());
        SlotDetailModel slot = slotQueryService.loadSlotById(request.getSlotId());
        if (!request.getOrganizationId().equals(slot.getOrganizationId())) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "号源与机构不匹配");
        }
        slotDomainService.allocateBooking(
            healthCheckupBookingMapper.toAllocateBookingCommand(request.getSlotId(), operatorId));
        Appointment appointment = appointmentDomainService.create(
            healthCheckupBookingMapper.toIndividualHealthCheckupAppointmentCommand(request, slot, operatorId));
        healthCheckupDomainService.saveBookingExt(
            healthCheckupBookingMapper.toHealthCheckBookingExt(
                appointment.getId(), pkg.getId(), null, pkg), operatorId);
        return healthCheckupBookingMapper.toCreateIndividualHealthCheckupBookingResponse(appointment);
    }

    private List<GroupCheckupRoster> resolveRostersForBatchBooking(BatchCreateHealthCheckupBookingRequest request) {
        if (request.getRosterIds() != null && !request.getRosterIds().isEmpty()) {
            List<GroupCheckupRoster> rows = new ArrayList<>();
            for (Long rosterId : request.getRosterIds()) {
                GroupCheckupRoster roster = healthCheckupQueryService.pageRoster(
                    request.getBatchId(), PageRequest.of(0, 10000)).getContent().stream()
                    .filter(item -> rosterId.equals(item.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "名单不存在"));
                rows.add(roster);
            }
            return rows;
        }
        return healthCheckupQueryService.listPendingRosterByBatch(request.getBatchId());
    }

    private SlotDetailModel resolveFirstBookableSlot(Long scheduleId, LocalDate bookingDate) {
        FindBookableSlotsQuery query = new FindBookableSlotsQuery();
        query.setScheduleId(scheduleId);
        query.setStartTime(bookingDate.atStartOfDay());
        query.setEndTime(bookingDate.plusDays(1).atStartOfDay());
        for (BookableSlotModel model : slotQueryService.findBookableSlots(query)) {
            if (Boolean.TRUE.equals(model.getIsFull())) {
                continue;
            }
            return slotQueryService.loadSlotById(model.getSlotId());
        }
        throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "团检批次日期无可约号源");
    }

    private AppointmentBookingDetailModel resolveHealthCheckupBooking(CheckInHealthCheckupRequest request) {
        if (request.getAppointmentId() != null) {
            return this.requireHealthCheckupBooking(request.getAppointmentId());
        }
        if (StringUtils.hasText(request.getAppointmentBusNo())) {
            ActiveOutpatientBookingLookupQuery query = new ActiveOutpatientBookingLookupQuery();
            query.setAppointmentBusNo(request.getAppointmentBusNo());
            query.setRegistrationDate(LocalDate.now());
            Optional<AppointmentBookingDetailModel> found =
                appointmentQueryService.findActiveOutpatientBookingForRegister(query);
            AppointmentBookingDetailModel booking = found.orElseThrow(
                () -> new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "体检预约不存在"));
            if (!HEALTH_CHECKUP_BOOKING_KIND.equals(booking.getAppointmentKind())) {
                throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "非体检预约");
            }
            return booking;
        }
        throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "appointmentId 与 appointmentBusNo 至少传一个");
    }

    private AppointmentBookingDetailModel requireHealthCheckupBooking(Long appointmentId) {
        AppointmentBookingDetailModel booking = appointmentQueryService.loadOutpatientBookingById(appointmentId);
        if (!HEALTH_CHECKUP_BOOKING_KIND.equals(booking.getAppointmentKind())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "非体检预约不允许此操作");
        }
        return booking;
    }
}
