/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.plugin.booking.application.healthcheckup.mapper.HealthCheckupBookingMapper;
import com.zxplat.plugin.booking.application.healthcheckup.request.BatchCreateHealthCheckupBookingRequest;
import com.zxplat.plugin.booking.application.healthcheckup.request.CheckInHealthCheckupRequest;
import com.zxplat.plugin.booking.application.healthcheckup.request.CreateIndividualHealthCheckupBookingRequest;
import com.zxplat.plugin.booking.application.healthcheckup.request.DeleteGroupCheckupBatchRequest;
import com.zxplat.plugin.booking.application.healthcheckup.request.DeleteGroupCheckupOrgRequest;
import com.zxplat.plugin.booking.application.healthcheckup.request.DeleteGroupCheckupRosterRequest;
import com.zxplat.plugin.booking.application.healthcheckup.request.DeleteHealthCheckPackageRequest;
import com.zxplat.plugin.booking.application.healthcheckup.request.ImportGroupCheckupRosterRequest;
import com.zxplat.plugin.booking.application.healthcheckup.request.PageGroupCheckupBatchesRequest;
import com.zxplat.plugin.booking.application.healthcheckup.request.PageGroupCheckupOrgsRequest;
import com.zxplat.plugin.booking.application.healthcheckup.request.PageGroupCheckupRostersRequest;
import com.zxplat.plugin.booking.application.healthcheckup.request.PageHealthCheckPackagesRequest;
import com.zxplat.plugin.booking.application.healthcheckup.request.SaveGroupCheckupBatchRequest;
import com.zxplat.plugin.booking.application.healthcheckup.request.SaveGroupCheckupOrgRequest;
import com.zxplat.plugin.booking.application.healthcheckup.request.SaveGroupCheckupRosterRequest;
import com.zxplat.plugin.booking.application.healthcheckup.request.SaveHealthCheckPackageRequest;
import com.zxplat.plugin.booking.application.healthcheckup.response.BatchCreateHealthCheckupBookingResponse;
import com.zxplat.plugin.booking.application.healthcheckup.response.CheckInHealthCheckupResponse;
import com.zxplat.plugin.booking.application.healthcheckup.response.CreateIndividualHealthCheckupBookingResponse;
import com.zxplat.plugin.booking.application.healthcheckup.response.GroupCheckupBatchDto;
import com.zxplat.plugin.booking.application.healthcheckup.response.GroupCheckupOrgDto;
import com.zxplat.plugin.booking.application.healthcheckup.response.GroupCheckupRosterDto;
import com.zxplat.plugin.booking.application.healthcheckup.response.HealthCheckPackageDto;
import com.zxplat.plugin.booking.application.healthcheckup.response.ImportGroupCheckupRosterResponse;
import com.zxplat.plugin.booking.application.healthcheckup.response.PageGroupCheckupBatchesResponse;
import com.zxplat.plugin.booking.application.healthcheckup.response.PageGroupCheckupOrgsResponse;
import com.zxplat.plugin.booking.application.healthcheckup.response.PageGroupCheckupRostersResponse;
import com.zxplat.plugin.booking.application.healthcheckup.response.PageHealthCheckPackagesResponse;
import com.zxplat.plugin.booking.application.healthcheckup.response.SaveGroupCheckupBatchResponse;
import com.zxplat.plugin.booking.application.healthcheckup.response.SaveGroupCheckupOrgResponse;
import com.zxplat.plugin.booking.application.healthcheckup.response.SaveGroupCheckupRosterResponse;
import com.zxplat.plugin.booking.application.healthcheckup.response.SaveHealthCheckPackageResponse;
import com.zxplat.plugin.booking.application.share.BookingOperatorResolver;
import com.zxplat.plugin.booking.application.share.BookingPagedResultBuilder;
import com.zxplat.plugin.booking.domain.appointment.entity.Appointment;
import com.zxplat.plugin.booking.domain.appointment.queries.ActiveOutpatientBookingLookupQuery;
import com.zxplat.plugin.booking.domain.appointment.queries.model.AppointmentBookingDetailModel;
import com.zxplat.plugin.booking.domain.appointment.service.AppointmentDomainService;
import com.zxplat.plugin.booking.domain.appointment.service.AppointmentQueryService;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.GroupCheckupBatch;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.GroupCheckupOrg;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.GroupCheckupRoster;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.HealthCheckPackage;
import com.zxplat.plugin.booking.domain.healthcheckup.service.HealthCheckupDomainService;
import com.zxplat.plugin.booking.domain.healthcheckup.service.HealthCheckupQueryService;
import com.zxplat.plugin.booking.domain.share.AppointmentKindConstants;
import com.zxplat.plugin.booking.domain.slot.queries.FindBookableSlotsQuery;
import com.zxplat.plugin.booking.domain.slot.queries.model.BookableSlotModel;
import com.zxplat.plugin.booking.domain.slot.queries.model.SlotDetailModel;
import com.zxplat.plugin.booking.domain.slot.service.SlotDomainService;
import com.zxplat.plugin.booking.domain.slot.service.SlotQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
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
 * 体检预约应用服务（H 线）。
 *
 * <p>职责边界：团检组织/批次/名单、体检套餐、个检/团检预约创建与到检签到；占号经 {@link SlotDomainService}。</p>
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/healthCheckupBooking/{methodName}}。</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class HealthCheckupBookingAppService {

    private static final String HEALTH_CHECKUP_BOOKING_KIND = AppointmentKindConstants.HEALTH_CHECKUP_BOOKING;

    /** 体检读侧：组织、批次、名单、套餐。 */
    private final HealthCheckupQueryService healthCheckupQueryService;
    /** 体检写侧：组织/批次/名单/套餐/预约扩展。 */
    private final HealthCheckupDomainService healthCheckupDomainService;
    /** 预约写侧：创建、报到入队。 */
    private final AppointmentDomainService appointmentDomainService;
    /** 预约读侧：按单号解析。 */
    private final AppointmentQueryService appointmentQueryService;
    /** 号源读侧。 */
    private final SlotQueryService slotQueryService;
    /** 号源写侧：allocate/release。 */
    private final SlotDomainService slotDomainService;
    /** Request/Command/DTO 映射。 */
    private final HealthCheckupBookingMapper healthCheckupBookingMapper;
    /** 写路径操作人。 */
    private final BookingOperatorResolver bookingOperatorResolver;

    /**
     * 团检单位分页。
     *
     * @param request 关键字与分页参数
     * @return 单位分页结果
     */
    public PageGroupCheckupOrgsResponse pageGroupCheckupOrgs(@Valid PageGroupCheckupOrgsRequest request) {
        // Pre：按关键字分页加载团检单位。
        Page<GroupCheckupOrg> page = healthCheckupQueryService.pageOrgs(
            request.getKeyword(), PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<GroupCheckupOrgDto> rows = new ArrayList<>();
        for (GroupCheckupOrg entity : page.getContent()) {
            rows.add(healthCheckupBookingMapper.toGroupCheckupOrgDto(entity));
        }
        // Asm：映射 DTO 并包装分页结果。
        return BookingPagedResultBuilder.build(new PageGroupCheckupOrgsResponse(), rows, page);
    }

    /** 保存团检单位。 */
    public SaveGroupCheckupOrgResponse saveGroupCheckupOrg(@Valid SaveGroupCheckupOrgRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：持久化团检单位。
        GroupCheckupOrg saved = healthCheckupDomainService.saveOrg(
            healthCheckupBookingMapper.toGroupCheckupOrgEntity(request), operatorId);
        SaveGroupCheckupOrgResponse response = new SaveGroupCheckupOrgResponse();
        response.setId(saved.getId());
        // Asm：返回主键。
        return response;
    }

    /** 删除团检单位。 */
    public void deleteGroupCheckupOrg(@Valid DeleteGroupCheckupOrgRequest request) {
        // Gate：写路径须登录。
        // Cmd：软删团检单位。
        healthCheckupDomainService.deleteOrg(request.getId(), bookingOperatorResolver.requireOperatorId());
    }

    /**
     * 团检批次分页。
     *
     * @param request 单位主键与分页参数
     * @return 批次分页结果
     */
    public PageGroupCheckupBatchesResponse pageGroupCheckupBatches(@Valid PageGroupCheckupBatchesRequest request) {
        // Pre：按单位分页加载团检批次并回填单位名称。
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
        // Asm：映射 DTO 并包装分页结果。
        return BookingPagedResultBuilder.build(new PageGroupCheckupBatchesResponse(), rows, page);
    }

    /** 保存团检批次。 */
    public SaveGroupCheckupBatchResponse saveGroupCheckupBatch(@Valid SaveGroupCheckupBatchRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：持久化团检批次。
        GroupCheckupBatch saved = healthCheckupDomainService.saveBatch(
            healthCheckupBookingMapper.toGroupCheckupBatchEntity(request), operatorId);
        SaveGroupCheckupBatchResponse response = new SaveGroupCheckupBatchResponse();
        response.setId(saved.getId());
        // Asm：返回主键。
        return response;
    }

    /** 删除团检批次。 */
    public void deleteGroupCheckupBatch(@Valid DeleteGroupCheckupBatchRequest request) {
        // Gate：写路径须登录。
        // Cmd：软删团检批次。
        healthCheckupDomainService.deleteBatch(request.getId(), bookingOperatorResolver.requireOperatorId());
    }

    /**
     * 团检名单分页。
     *
     * @param request 批次主键与分页参数
     * @return 名单分页结果
     */
    public PageGroupCheckupRostersResponse pageGroupCheckupRosters(@Valid PageGroupCheckupRostersRequest request) {
        // Pre：按批次分页加载团检名单。
        Page<GroupCheckupRoster> page = healthCheckupQueryService.pageRoster(
            request.getBatchId(), PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<GroupCheckupRosterDto> rows = new ArrayList<>();
        for (GroupCheckupRoster entity : page.getContent()) {
            rows.add(healthCheckupBookingMapper.toGroupCheckupRosterDto(entity));
        }
        // Asm：映射 DTO 并包装分页结果。
        return BookingPagedResultBuilder.build(new PageGroupCheckupRostersResponse(), rows, page);
    }

    /** 保存团检名单行。 */
    public SaveGroupCheckupRosterResponse saveGroupCheckupRoster(@Valid SaveGroupCheckupRosterRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：持久化团检名单行。
        GroupCheckupRoster saved = healthCheckupDomainService.saveRoster(
            healthCheckupBookingMapper.toGroupCheckupRosterEntity(request), operatorId);
        SaveGroupCheckupRosterResponse response = new SaveGroupCheckupRosterResponse();
        response.setId(saved.getId());
        // Asm：返回主键。
        return response;
    }

    /** 删除团检名单行。 */
    public void deleteGroupCheckupRoster(@Valid DeleteGroupCheckupRosterRequest request) {
        // Gate：写路径须登录。
        // Cmd：软删团检名单行。
        healthCheckupDomainService.deleteRoster(request.getId(), bookingOperatorResolver.requireOperatorId());
    }

    /**
     * CSV 导入团检名单。
     *
     * @param request 批次主键与 CSV 内容
     * @return 导入条数
     */
    public ImportGroupCheckupRosterResponse importGroupCheckupRoster(@Valid ImportGroupCheckupRosterRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：校验批次存在并逐行解析 CSV 名单。
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
        // Asm：返回 CSV 导入条数。
        ImportGroupCheckupRosterResponse response = new ImportGroupCheckupRosterResponse();
        response.setImportedCount(imported);
        return response;
    }

    /**
     * 批量创建团检预约：按批次为待预约名单占号并写扩展。
     *
     * @param request 批次主键与可选名单 ID 列表
     * @return 成功预约人数
     */
    public BatchCreateHealthCheckupBookingResponse batchCreateHealthCheckupBooking(
        @Valid BatchCreateHealthCheckupBookingRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：加载批次、单位、套餐与待预约名单，解析首个可约号源。
        GroupCheckupBatch batch = healthCheckupQueryService.loadBatchById(request.getBatchId());
        if (batch.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "团检批次未关联日程");
        }
        GroupCheckupOrg org = healthCheckupQueryService.loadOrgById(batch.getOrgId());
        HealthCheckPackage pkg;
        if (batch.getPackageId() == null) {
            pkg = null;
        } else {
            pkg = healthCheckupQueryService.loadPackageById(batch.getPackageId());
        }
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
        // Cmd：逐名单占号、建预约、写扩展并更新名单/批次计数。
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
        // Asm：返回成功预约人数。
        BatchCreateHealthCheckupBookingResponse response = new BatchCreateHealthCheckupBookingResponse();
        response.setBookedCount(booked);
        return response;
    }

    /**
     * 体检到检签到：已报到则幂等返回，否则入队。
     *
     * @param request 预约主键或业务号、目标诊室
     * @return 报到序号
     */
    public CheckInHealthCheckupResponse checkInHealthCheckup(@Valid CheckInHealthCheckupRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：解析体检预约；已报到直接返回。
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
        // Cmd：入队报到。
        Appointment queueItem = appointmentDomainService.checkInToQueue(
            healthCheckupBookingMapper.toCheckInToQueueCommand(booking, destinationLocationId, operatorId));
        // Asm：映射报到响应。
        response.setAppointmentId(queueItem.getId());
        response.setDisplayOrder(queueItem.getDisplayOrder());
        return response;
    }

    /**
     * 体检套餐分页。
     *
     * @param request 机构与分页参数
     * @return 套餐分页结果
     */
    public PageHealthCheckPackagesResponse pageHealthCheckPackages(@Valid PageHealthCheckPackagesRequest request) {
        // Pre：按机构分页加载体检套餐。
        Page<HealthCheckPackage> page = healthCheckupQueryService.pagePackages(
            request.getOrganizationId(), PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<HealthCheckPackageDto> rows = new ArrayList<>();
        for (HealthCheckPackage entity : page.getContent()) {
            rows.add(healthCheckupBookingMapper.toHealthCheckPackageDto(entity));
        }
        // Asm：映射 DTO 并包装分页结果。
        return BookingPagedResultBuilder.build(new PageHealthCheckPackagesResponse(), rows, page);
    }

    /**
     * 保存体检套餐（含明细项全量替换）。
     *
     * @param request 套餐头与明细
     * @return 保存后的主键
     */
    public SaveHealthCheckPackageResponse saveHealthCheckPackage(@Valid SaveHealthCheckPackageRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：保存套餐头。
        HealthCheckPackage saved = healthCheckupDomainService.savePackage(
            healthCheckupBookingMapper.toHealthCheckPackageEntity(request), operatorId);
        // Cmd：替换套餐明细项。
        if (request.getItems() != null) {
            healthCheckupDomainService.replacePackageItems(
                saved.getId(),
                healthCheckupBookingMapper.toHealthCheckPackageItems(saved.getId(), request.getItems()),
                operatorId);
        }
        // Asm：返回主键。
        SaveHealthCheckPackageResponse response = new SaveHealthCheckPackageResponse();
        response.setId(saved.getId());
        return response;
    }

    /** 删除体检套餐。 */
    public void deleteHealthCheckPackage(@Valid DeleteHealthCheckPackageRequest request) {
        // Gate：写路径须登录。
        // Cmd：软删体检套餐。
        healthCheckupDomainService.deletePackage(request.getId(), bookingOperatorResolver.requireOperatorId());
    }

    /**
     * 创建个检预约：占号 + 写 Appointment + 体检扩展。
     *
     * @param request 患者、套餐、号源与机构
     * @return 新预约主键与业务号
     */
    public CreateIndividualHealthCheckupBookingResponse createIndividualHealthCheckupBooking(
        @Valid CreateIndividualHealthCheckupBookingRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：加载套餐与号源并校验机构。
        HealthCheckPackage pkg = healthCheckupQueryService.loadPackageById(request.getPackageId());
        SlotDetailModel slot = slotQueryService.loadSlotById(request.getSlotId());
        if (!request.getOrganizationId().equals(slot.getOrganizationId())) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "号源与机构不匹配");
        }
        // Cmd：占号、建预约、写体检扩展。
        slotDomainService.allocateBooking(
            healthCheckupBookingMapper.toAllocateBookingCommand(request.getSlotId(), operatorId));
        Appointment appointment = appointmentDomainService.create(
            healthCheckupBookingMapper.toIndividualHealthCheckupAppointmentCommand(request, slot, operatorId));
        healthCheckupDomainService.saveBookingExt(
            healthCheckupBookingMapper.toHealthCheckBookingExt(
                appointment.getId(), pkg.getId(), null, pkg), operatorId);
        // Asm：映射创建响应。
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
