package com.openhis.whale.booking.application.healthcheckup.mapper;

import com.openhis.whale.booking.application.healthcheckup.request.CreateIndividualHealthCheckupBookingRequest;
import com.openhis.whale.booking.application.healthcheckup.request.HealthCheckPackageItemRequest;
import com.openhis.whale.booking.application.healthcheckup.request.SaveGroupCheckupBatchRequest;
import com.openhis.whale.booking.application.healthcheckup.request.SaveGroupCheckupOrgRequest;
import com.openhis.whale.booking.application.healthcheckup.request.SaveGroupCheckupRosterRequest;
import com.openhis.whale.booking.application.healthcheckup.request.SaveHealthCheckPackageRequest;
import com.openhis.whale.booking.application.healthcheckup.response.CreateIndividualHealthCheckupBookingResponse;
import com.openhis.whale.booking.application.healthcheckup.response.GroupCheckupBatchDto;
import com.openhis.whale.booking.application.healthcheckup.response.GroupCheckupOrgDto;
import com.openhis.whale.booking.application.healthcheckup.response.GroupCheckupRosterDto;
import com.openhis.whale.booking.application.healthcheckup.response.HealthCheckPackageDto;
import com.openhis.whale.booking.domain.appointment.command.CheckInToQueueCommand;
import com.openhis.whale.booking.domain.appointment.command.CreateAppointmentCommand;
import com.openhis.whale.booking.domain.appointment.entity.Appointment;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentBookingDetailModel;
import com.openhis.whale.booking.domain.healthcheckup.entity.GroupCheckupBatch;
import com.openhis.whale.booking.domain.healthcheckup.entity.GroupCheckupOrg;
import com.openhis.whale.booking.domain.healthcheckup.entity.GroupCheckupRoster;
import com.openhis.whale.booking.domain.healthcheckup.entity.HealthCheckBookingExt;
import com.openhis.whale.booking.domain.healthcheckup.entity.HealthCheckPackage;
import com.openhis.whale.booking.domain.healthcheckup.entity.HealthCheckPackageItem;
import com.openhis.whale.booking.domain.share.AppointmentKindConstants;
import com.openhis.whale.booking.domain.slot.command.AllocateBookingCommand;
import com.openhis.whale.booking.domain.slot.queries.model.SlotDetailModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class HealthCheckupBookingMapper {

    private static final String HEALTH_CHECKUP_BOOKING_KIND = AppointmentKindConstants.HEALTH_CHECKUP_BOOKING;
    private static final String CHECK_IN_ONLY_KIND = AppointmentKindConstants.CHECK_IN_ONLY;
    private static final String DEFAULT_ORIGIN_CHANNEL = "WINDOW";

    /**
     * 团检单位实体 → DTO。
     *
     * @param entity 团检单位实体
     * @return 团检单位 DTO
     */
    public GroupCheckupOrgDto toGroupCheckupOrgDto(GroupCheckupOrg entity) {
        GroupCheckupOrgDto dto = new GroupCheckupOrgDto();
        dto.setId(entity.getId());
        dto.setOrgCode(entity.getOrgCode());
        dto.setOrgName(entity.getOrgName());
        dto.setContactName(entity.getContactName());
        dto.setContactPhone(entity.getContactPhone());
        dto.setSettlementMode(entity.getSettlementMode());
        dto.setContractYear(entity.getContractYear());
        return dto;
    }

    /**
     * 保存团检单位请求 → 领域实体。
     *
     * @param request 保存团检单位请求
     * @return 团检单位实体
     */
    public GroupCheckupOrg toGroupCheckupOrgEntity(SaveGroupCheckupOrgRequest request) {
        GroupCheckupOrg entity = new GroupCheckupOrg();
        entity.setId(request.getId());
        entity.setOrgCode(request.getOrgCode());
        entity.setOrgName(request.getOrgName());
        entity.setContactName(request.getContactName());
        entity.setContactPhone(request.getContactPhone());
        entity.setSettlementMode(request.getSettlementMode());
        entity.setContractYear(request.getContractYear());
        return entity;
    }

    /**
     * 团检批次实体 → DTO（附加单位名称）。
     *
     * @param entity 团检批次实体
     * @param orgName 团检单位名称
     * @return 团检批次 DTO
     */
    public GroupCheckupBatchDto toGroupCheckupBatchDto(GroupCheckupBatch entity, String orgName) {
        GroupCheckupBatchDto dto = new GroupCheckupBatchDto();
        dto.setId(entity.getId());
        dto.setOrgId(entity.getOrgId());
        dto.setOrgName(orgName);
        dto.setBatchName(entity.getBatchName());
        dto.setPackageId(entity.getPackageId());
        dto.setScheduleId(entity.getScheduleId());
        dto.setBookingDate(entity.getBookingDate());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setCapacityLimit(entity.getCapacityLimit());
        dto.setBookedCount(entity.getBookedCount());
        dto.setStatusCode(entity.getStatusCode());
        return dto;
    }

    /**
     * 保存团检批次请求 → 领域实体。
     *
     * @param request 保存团检批次请求
     * @return 团检批次实体
     */
    public GroupCheckupBatch toGroupCheckupBatchEntity(SaveGroupCheckupBatchRequest request) {
        GroupCheckupBatch entity = new GroupCheckupBatch();
        entity.setId(request.getId());
        entity.setOrgId(request.getOrgId());
        entity.setBatchName(request.getBatchName());
        entity.setPackageId(request.getPackageId());
        entity.setScheduleId(request.getScheduleId());
        entity.setBookingDate(request.getBookingDate());
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        if (request.getCapacityLimit() != null) {
            entity.setCapacityLimit(request.getCapacityLimit());
        }
        if (request.getStatusCode() != null) {
            entity.setStatusCode(request.getStatusCode());
        }
        return entity;
    }

    /**
     * 团检花名册实体 → DTO。
     *
     * @param entity 团检花名册实体
     * @return 团检花名册 DTO
     */
    public GroupCheckupRosterDto toGroupCheckupRosterDto(GroupCheckupRoster entity) {
        GroupCheckupRosterDto dto = new GroupCheckupRosterDto();
        dto.setId(entity.getId());
        dto.setBatchId(entity.getBatchId());
        dto.setPatientId(entity.getPatientId());
        dto.setFullName(entity.getFullName());
        dto.setIdCardNo(entity.getIdCardNo());
        dto.setStudentNo(entity.getStudentNo());
        dto.setMobilePhone(entity.getMobilePhone());
        dto.setAppointmentId(entity.getAppointmentId());
        dto.setBookingStatusCode(entity.getBookingStatusCode());
        return dto;
    }

    /**
     * 保存团检花名册请求 → 领域实体。
     *
     * @param request 保存团检花名册请求
     * @return 团检花名册实体
     */
    public GroupCheckupRoster toGroupCheckupRosterEntity(SaveGroupCheckupRosterRequest request) {
        GroupCheckupRoster entity = new GroupCheckupRoster();
        entity.setId(request.getId());
        entity.setBatchId(request.getBatchId());
        entity.setPatientId(request.getPatientId());
        entity.setFullName(request.getFullName());
        entity.setIdCardNo(request.getIdCardNo());
        entity.setStudentNo(request.getStudentNo());
        entity.setMobilePhone(request.getMobilePhone());
        return entity;
    }

    /**
     * CSV 行 → 团检花名册实体。
     *
     * @param batchId 团检批次主键
     * @param columns CSV 列数组
     * @return 团检花名册实体
     */
    public GroupCheckupRoster toGroupCheckupRosterFromCsvRow(Long batchId, String[] columns) {
        GroupCheckupRoster entity = new GroupCheckupRoster();
        entity.setBatchId(batchId);
        entity.setFullName(columns[0].trim());
        if (columns.length > 1) {
            entity.setIdCardNo(columns[1].trim());
        }
        if (columns.length > 2) {
            entity.setStudentNo(columns[2].trim());
        }
        if (columns.length > 3) {
            entity.setMobilePhone(columns[3].trim());
        }
        entity.setBookingStatusCode("PENDING");
        entity.setImportStatusCode("IMPORTED");
        return entity;
    }

    /**
     * 体检套餐实体 → DTO。
     *
     * @param entity 体检套餐实体
     * @return 体检套餐 DTO
     */
    public HealthCheckPackageDto toHealthCheckPackageDto(HealthCheckPackage entity) {
        HealthCheckPackageDto dto = new HealthCheckPackageDto();
        dto.setId(entity.getId());
        dto.setOrganizationId(entity.getOrganizationId());
        dto.setPackageCode(entity.getPackageCode());
        dto.setPackageName(entity.getPackageName());
        dto.setApplicableGender(entity.getApplicableGender());
        dto.setApplicableAgeMin(entity.getApplicableAgeMin());
        dto.setApplicableAgeMax(entity.getApplicableAgeMax());
        dto.setEstimatedAmount(entity.getEstimatedAmount());
        dto.setActiveFlag(entity.getActiveFlag());
        return dto;
    }

    /**
     * 保存体检套餐请求 → 领域实体。
     *
     * @param request 保存体检套餐请求
     * @return 体检套餐实体
     */
    public HealthCheckPackage toHealthCheckPackageEntity(SaveHealthCheckPackageRequest request) {
        HealthCheckPackage entity = new HealthCheckPackage();
        entity.setId(request.getId());
        entity.setOrganizationId(request.getOrganizationId());
        entity.setPackageCode(request.getPackageCode());
        entity.setPackageName(request.getPackageName());
        entity.setApplicableGender(request.getApplicableGender());
        entity.setApplicableAgeMin(request.getApplicableAgeMin());
        entity.setApplicableAgeMax(request.getApplicableAgeMax());
        entity.setEstimatedAmount(request.getEstimatedAmount());
        if (request.getActiveFlag() != null) {
            entity.setActiveFlag(request.getActiveFlag());
        } else {
            entity.setActiveFlag(Boolean.TRUE);
        }
        return entity;
    }

    /**
     * 套餐明细请求 → 套餐明细实体列表。
     *
     * @param packageId 体检套餐主键
     * @param items 套餐明细请求列表
     * @return 体检套餐明细实体列表
     */
    public List<HealthCheckPackageItem> toHealthCheckPackageItems(Long packageId, List<HealthCheckPackageItemRequest> items) {
        List<HealthCheckPackageItem> rows = new ArrayList<>();
        if (items == null) {
            return rows;
        }
        int sort = 1;
        for (HealthCheckPackageItemRequest item : items) {
            HealthCheckPackageItem entity = new HealthCheckPackageItem();
            entity.setPackageId(packageId);
            entity.setActivityDefinitionId(item.getActivityDefinitionId());
            entity.setItemName(item.getItemName());
            if (item.getRequiredFlag() == null) {
                entity.setRequiredFlag(Boolean.TRUE);
            } else {
                entity.setRequiredFlag(item.getRequiredFlag());
            }
            if (item.getSortOrder() == null) {
                entity.setSortOrder(sort);
            } else {
                entity.setSortOrder(item.getSortOrder());
            }
            rows.add(entity);
            sort++;
        }
        return rows;
    }

    /**
     * 占号命令。
     *
     * @param slotId 号源主键
     * @param operatorId 操作人主键
     * @return 占号命令
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
     * 团检批次 + 花名册 → 创建体检预约命令。
     *
     * @param batch 团检批次实体
     * @param roster 团检花名册实体
     * @param slot 目标号源明细
     * @param organizationId 机构主键
     * @param operatorId 操作人主键
     * @return 创建预约命令
     */
    public CreateAppointmentCommand toGroupHealthCheckupAppointmentCommand(
        GroupCheckupBatch batch,
        GroupCheckupRoster roster,
        SlotDetailModel slot,
        Long organizationId,
        Long operatorId) {
        CreateAppointmentCommand command = new CreateAppointmentCommand();
        command.setOperatorId(operatorId);
        CreateAppointmentCommand.CreateAppointmentItemCommand item = new CreateAppointmentCommand.CreateAppointmentItemCommand();
        item.setAppointmentKind(HEALTH_CHECKUP_BOOKING_KIND);
        item.setOrganizationId(organizationId);
        item.setPatientId(roster.getPatientId());
        item.setSlotId(slot.getId());
        item.setStartInstant(slot.getStartTime());
        item.setEndInstant(slot.getEndTime());
        CreateAppointmentCommand.AppointmentOriginCommand origin = new CreateAppointmentCommand.AppointmentOriginCommand();
        origin.setOriginChannel(DEFAULT_ORIGIN_CHANNEL);
        origin.setOriginOperatorId(operatorId);
        item.setOrigin(origin);
        command.setItem(item);
        return command;
    }

    /**
     * 个人体检预约请求 → 创建预约命令。
     *
     * @param request 创建个人体检预约请求
     * @param slot 目标号源明细
     * @param operatorId 操作人主键
     * @return 创建预约命令
     */
    public CreateAppointmentCommand toIndividualHealthCheckupAppointmentCommand(
        CreateIndividualHealthCheckupBookingRequest request,
        SlotDetailModel slot,
        Long operatorId) {
        CreateAppointmentCommand command = new CreateAppointmentCommand();
        command.setOperatorId(operatorId);
        CreateAppointmentCommand.CreateAppointmentItemCommand item = new CreateAppointmentCommand.CreateAppointmentItemCommand();
        item.setAppointmentKind(HEALTH_CHECKUP_BOOKING_KIND);
        item.setOrganizationId(request.getOrganizationId());
        item.setPatientId(request.getPatientId());
        item.setSlotId(request.getSlotId());
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
     * 预约主键 + 套餐/批次 → 体检预约扩展实体。
     *
     * @param appointmentId 预约主键
     * @param packageId 体检套餐主键
     * @param batchId 团检批次主键
     * @param pkg 体检套餐实体
     * @return 体检预约扩展实体
     */
    public HealthCheckBookingExt toHealthCheckBookingExt(Long appointmentId, Long packageId, Long batchId, HealthCheckPackage pkg) {
        HealthCheckBookingExt ext = new HealthCheckBookingExt();
        ext.setAppointmentId(appointmentId);
        ext.setPackageId(packageId);
        ext.setBatchId(batchId);
        if (pkg != null) {
            ext.setEstimatedAmount(pkg.getEstimatedAmount());
            ext.setPackageSnapshotJson(pkg.getPackageName());
        }
        return ext;
    }

    /**
     * 预约实体 → 创建个人体检预约响应。
     *
     * @param appointment 预约实体
     * @return 创建个人体检预约响应
     */
    public CreateIndividualHealthCheckupBookingResponse toCreateIndividualHealthCheckupBookingResponse(Appointment appointment) {
        CreateIndividualHealthCheckupBookingResponse response = new CreateIndividualHealthCheckupBookingResponse();
        response.setAppointmentId(appointment.getId());
        response.setBusNo(appointment.getBusNo());
        return response;
    }

    /**
     * 预约详情 → 现场报到入队命令。
     *
     * @param booking 预约详情模型
     * @param destinationLocationId 目的地位置主键
     * @param operatorId 操作人主键
     * @return 现场报到入队命令
     */
    public CheckInToQueueCommand toCheckInToQueueCommand(
        AppointmentBookingDetailModel booking,
        Long destinationLocationId,
        Long operatorId) {
        CheckInToQueueCommand command = new CheckInToQueueCommand();
        command.setOperatorId(operatorId);
        CheckInToQueueCommand.CheckInToQueueItemCommand item = new CheckInToQueueCommand.CheckInToQueueItemCommand();
        item.setAppointmentKind(CHECK_IN_ONLY_KIND);
        item.setOrganizationId(booking.getOrganizationId());
        item.setDestinationLocationId(destinationLocationId);
        item.setPatientId(booking.getPatientId());
        CreateAppointmentCommand.AppointmentOriginCommand origin = new CreateAppointmentCommand.AppointmentOriginCommand();
        origin.setOriginChannel(DEFAULT_ORIGIN_CHANNEL);
        origin.setOriginOperatorId(operatorId);
        item.setOrigin(origin);
        command.setItem(item);
        return command;
    }
}
