/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.medtech.mapper;

import com.openhis.whale.booking.application.medtech.request.CancelDiagnosticBookingRequest;
import com.openhis.whale.booking.application.medtech.request.CreateDiagnosticBookingRequest;
import com.openhis.whale.booking.application.medtech.request.SaveDiagnosticResourceMapRequest;
import com.openhis.whale.booking.application.medtech.request.SearchEncounterRequest;
import com.openhis.whale.booking.application.medtech.response.CreateDiagnosticBookingResponse;
import com.openhis.whale.booking.application.medtech.response.DiagnosticResourceMapDto;
import com.openhis.whale.booking.application.medtech.response.EncounterContextDto;
import com.openhis.whale.booking.application.medtech.response.ListDiagnosticBookableSlotsResponse;
import com.openhis.whale.booking.application.medtech.response.LoadMedTechFormInitResponse;
import com.openhis.whale.booking.application.medtech.response.RescheduleDiagnosticBookingResponse;
import com.openhis.whale.booking.application.medtech.response.ServiceRequestItemDto;
import com.openhis.whale.booking.application.register.mapper.BookingRegisterMapper;
import com.openhis.whale.booking.application.register.response.BookableSlotDto;
import com.openhis.whale.booking.application.register.response.ListBookableSlotsResponse;
import com.openhis.whale.booking.domain.medtech.result.BookableServiceRequestDto;
import com.openhis.whale.booking.domain.masterdata.result.EncounterSearchResultDto;
import com.openhis.whale.booking.domain.masterdata.model.MasterDataOption;
import com.openhis.whale.booking.domain.masterdata.result.ScheduleFormMasterDataSnapshot;
import com.openhis.whale.booking.domain.masterdata.queries.SearchEncounterQuery;
import com.openhis.whale.booking.domain.appointment.command.CancelAppointmentCommand;
import com.openhis.whale.booking.domain.appointment.command.CheckInToQueueCommand;
import com.openhis.whale.booking.domain.appointment.command.CreateAppointmentCommand;
import com.openhis.whale.booking.domain.appointment.entity.Appointment;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentBookingDetailModel;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentBookingListModel;
import com.openhis.whale.booking.domain.diagnostic.entity.DiagnosticResourceMap;
import com.openhis.whale.booking.domain.share.AppointmentKindConstants;
import com.openhis.whale.booking.domain.slot.command.AllocateBookingCommand;
import com.openhis.whale.booking.domain.slot.command.ReleaseBookingCommand;
import com.openhis.whale.booking.domain.slot.queries.model.SlotDetailModel;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 医技预约应用层映射器。
 */
@Component
public class MedTechBookingMapper {

    private static final String DEFAULT_ORIGIN_CHANNEL = "WINDOW";

    private final BookingRegisterMapper bookingRegisterMapper;

    /**
     * @param bookingRegisterMapper 门诊预约映射器
     */
    public MedTechBookingMapper(BookingRegisterMapper bookingRegisterMapper) {
        this.bookingRegisterMapper = bookingRegisterMapper;
    }

    /**
     * 主数据 → 医技预约表单初始化响应。
     *
     * @param locations 诊室位置选项列表
     * @param masterData 排班表单主数据快照
     * @return 医技预约表单初始化响应
     */
    public LoadMedTechFormInitResponse toLoadMedTechFormInitResponse(
        List<MasterDataOption> locations,
        ScheduleFormMasterDataSnapshot masterData) {
        LoadMedTechFormInitResponse response = new LoadMedTechFormInitResponse();
        response.setClinicLocations(this.toOptionDtos(locations));
        if (masterData != null) {
            response.setHealthcareServices(this.toOptionDtos(masterData.getHealthcareServices()));
        }
        return response;
    }

    /**
     * 检索就诊请求 → HealthEncounterReader 查询。
     *
     * @param request 检索就诊请求
     * @return 就诊检索 Reader 查询
     */
    public SearchEncounterQuery toSearchEncounterQuery(SearchEncounterRequest request) {
        SearchEncounterQuery query = new SearchEncounterQuery();
        query.setOrganizationId(request.getOrganizationId());
        query.setKeyword(request.getKeyword());
        query.setEncounterBusNo(request.getEncounterBusNo());
        query.setPatientId(request.getPatientId());
        return query;
    }

    /**
     * HealthEncounterReader 就诊检索结果 → 就诊上下文 DTO。
     *
     * @param source HealthEncounterReader 就诊检索结果
     * @return 就诊上下文 DTO，source 为 null 时返回 null
     */
    public EncounterContextDto toEncounterContextDto(EncounterSearchResultDto source) {
        if (source == null) {
            return null;
        }
        EncounterContextDto dto = new EncounterContextDto();
        dto.setEncounterId(source.getEncounterId());
        dto.setEncounterBusNo(source.getEncounterBusNo());
        dto.setPatientId(source.getPatientId());
        dto.setPatientName(source.getPatientName());
        dto.setOrganizationId(source.getOrganizationId());
        dto.setOrganizationName(source.getOrganizationName());
        return dto;
    }

    /**
     * 可预约医嘱 + 预约索引 → 医嘱列表行 DTO。
     *
     * @param source 可预约医嘱 DTO
     * @param appointmentIdByServiceRequestId 医嘱主键 → 预约主键映射
     * @return 医嘱列表行 DTO
     */
    public ServiceRequestItemDto toServiceRequestItemDto(
        BookableServiceRequestDto source,
        Map<Long, Long> appointmentIdByServiceRequestId) {
        ServiceRequestItemDto dto = new ServiceRequestItemDto();
        if (source == null) {
            return dto;
        }
        dto.setServiceRequestId(source.getServiceRequestId());
        dto.setServiceRequestBusNo(source.getServiceRequestBusNo());
        dto.setActivityName(source.getActivityName());
        dto.setStatusView(source.getStatusEnum());
        Long appointmentId = appointmentIdByServiceRequestId.get(source.getServiceRequestId());
        dto.setAppointmentId(appointmentId);
        dto.setBookedFlag(appointmentId != null);
        return dto;
    }

    /**
     * 预约列表模型 + 可预约医嘱 → 医嘱列表行 DTO。
     *
     * @param model 预约列表模型
     * @param source 可预约医嘱 DTO
     * @return 医嘱列表行 DTO
     */
    public ServiceRequestItemDto toServiceRequestItemDto(AppointmentBookingListModel model, BookableServiceRequestDto source) {
        ServiceRequestItemDto dto = new ServiceRequestItemDto();
        if (source != null) {
            dto.setServiceRequestId(source.getServiceRequestId());
            dto.setServiceRequestBusNo(source.getServiceRequestBusNo());
            dto.setActivityName(source.getActivityName());
            dto.setStatusView(source.getStatusEnum());
        }
        if (model != null) {
            dto.setAppointmentId(model.getId());
            dto.setBookedFlag(Boolean.TRUE);
        }
        return dto;
    }

    /**
     * 预约列表模型 → 医嘱列表行 DTO（从预约反查医嘱）。
     *
     * @param model 预约列表模型
     * @return 医嘱列表行 DTO
     */
    public ServiceRequestItemDto toServiceRequestItemDtoFromAppointment(AppointmentBookingListModel model) {
        ServiceRequestItemDto dto = new ServiceRequestItemDto();
        if (model == null) {
            return dto;
        }
        dto.setAppointmentId(model.getId());
        dto.setBookedFlag(Boolean.TRUE);
        dto.setStatusView(model.getStatusView());
        if (model.getAppointmentTypeCode() != null) {
            try {
                dto.setServiceRequestId(Long.valueOf(model.getAppointmentTypeCode()));
            } catch (NumberFormatException ignored) {
                // 非医嘱关联预约
            }
        }
        return dto;
    }

    /**
     * 可约号源响应 → 医技可约号源响应。
     *
     * @param source 可约号源列表响应
     * @return 医技可约号源列表响应
     */
    public ListDiagnosticBookableSlotsResponse toListDiagnosticBookableSlotsResponse(ListBookableSlotsResponse source) {
        ListDiagnosticBookableSlotsResponse response = new ListDiagnosticBookableSlotsResponse();
        if (source == null || source.getSlots() == null) {
            response.setSlots(new ArrayList<>());
            return response;
        }
        List<BookableSlotDto> slots = new ArrayList<>();
        for (BookableSlotDto slot : source.getSlots()) {
            slots.add(slot);
        }
        response.setSlots(slots);
        return response;
    }

    /**
     * 占号命令（委托门诊预约映射器）。
     *
     * @param slotId 号源主键
     * @param operatorId 操作人主键
     * @return 占号命令
     */
    public AllocateBookingCommand toAllocateBookingCommand(Long slotId, Long operatorId) {
        return bookingRegisterMapper.toAllocateBookingCommand(slotId, operatorId);
    }

    /**
     * 释放占号命令（委托门诊预约映射器）。
     *
     * @param slotId 号源主键
     * @param operatorId 操作人主键
     * @return 释放占号命令
     */
    public ReleaseBookingCommand toReleaseBookingCommand(Long slotId, Long operatorId) {
        return bookingRegisterMapper.toReleaseBookingCommand(slotId, operatorId);
    }

    /**
     * 创建医技预约请求 → 创建预约命令。
     *
     * @param request 创建医技预约请求
     * @param slot 目标号源明细
     * @param resourceMap 医技资源映射
     * @param encounterId 就诊主键
     * @param operatorId 操作人主键
     * @return 创建预约命令
     */
    public CreateAppointmentCommand toCreateDiagnosticAppointmentCommand(
        CreateDiagnosticBookingRequest request,
        SlotDetailModel slot,
        DiagnosticResourceMap resourceMap,
        Long encounterId,
        Long operatorId) {
        CreateAppointmentCommand command = new CreateAppointmentCommand();
        command.setOperatorId(operatorId);
        CreateAppointmentCommand.CreateAppointmentItemCommand item = new CreateAppointmentCommand.CreateAppointmentItemCommand();
        item.setAppointmentKind(AppointmentKindConstants.DIAGNOSTIC_BOOKING);
        item.setOrganizationId(request.getOrganizationId());
        item.setPatientId(request.getPatientId());
        item.setEncounterId(encounterId);
        item.setSlotId(request.getSlotId());
        item.setAppointmentTypeCode(String.valueOf(request.getServiceRequestId()));
        item.setStartInstant(slot.getStartTime());
        item.setEndInstant(slot.getEndTime());
        if (resourceMap != null && resourceMap.getLocationId() != null) {
            item.setDestinationLocationId(resourceMap.getLocationId());
        }
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
     * 预约实体 → 创建医技预约响应。
     *
     * @param appointment 预约实体
     * @return 创建医技预约响应
     */
    public CreateDiagnosticBookingResponse toCreateDiagnosticBookingResponse(Appointment appointment) {
        CreateDiagnosticBookingResponse response = new CreateDiagnosticBookingResponse();
        response.setAppointmentId(appointment.getId());
        response.setBusNo(appointment.getBusNo());
        return response;
    }

    /**
     * 取消医技预约请求 → 取消预约命令。
     *
     * @param request 取消医技预约请求
     * @param operatorId 操作人主键
     * @return 取消预约命令
     */
    public CancelAppointmentCommand toCancelAppointmentCommand(CancelDiagnosticBookingRequest request, Long operatorId) {
        CancelAppointmentCommand command = new CancelAppointmentCommand();
        command.setOperatorId(operatorId);
        CancelAppointmentCommand.CancelAppointmentItemCommand item = new CancelAppointmentCommand.CancelAppointmentItemCommand();
        item.setAppointmentId(request.getAppointmentId());
        item.setReason(request.getReason());
        command.setItem(item);
        return command;
    }

    /**
     * 改约结果 → 医技改约响应。
     *
     * @param appointmentId 预约主键
     * @param newSlotId 新号源主键
     * @return 医技改约响应
     */
    public RescheduleDiagnosticBookingResponse toRescheduleDiagnosticBookingResponse(
        Long appointmentId,
        Long newSlotId) {
        RescheduleDiagnosticBookingResponse response = new RescheduleDiagnosticBookingResponse();
        response.setAppointmentId(appointmentId);
        response.setNewSlotId(newSlotId);
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
        item.setAppointmentKind(AppointmentKindConstants.CHECK_IN_ONLY);
        item.setOrganizationId(booking.getOrganizationId());
        item.setDestinationLocationId(destinationLocationId);
        item.setEncounterId(booking.getEncounterId());
        item.setPatientId(booking.getPatientId());
        CreateAppointmentCommand.AppointmentOriginCommand origin = new CreateAppointmentCommand.AppointmentOriginCommand();
        origin.setOriginChannel(DEFAULT_ORIGIN_CHANNEL);
        origin.setOriginOperatorId(operatorId);
        item.setOrigin(origin);
        command.setItem(item);
        return command;
    }

    /**
     * 医技资源映射实体 → DTO。
     *
     * @param entity 医技资源映射实体
     * @return 医技资源映射 DTO
     */
    public DiagnosticResourceMapDto toDiagnosticResourceMapDto(DiagnosticResourceMap entity) {
        DiagnosticResourceMapDto dto = new DiagnosticResourceMapDto();
        dto.setId(entity.getId());
        dto.setOrganizationId(entity.getOrganizationId());
        dto.setActivityDefinitionId(entity.getActivityDefinitionId());
        dto.setServiceCategoryCode(entity.getServiceCategoryCode());
        dto.setScheduleId(entity.getScheduleId());
        dto.setLocationId(entity.getLocationId());
        dto.setDeviceId(entity.getDeviceId());
        dto.setResourceName(entity.getResourceName());
        dto.setActiveFlag(entity.getActiveFlag());
        return dto;
    }

    /**
     * 保存医技资源映射请求 → 领域实体。
     *
     * @param request 保存医技资源映射请求
     * @return 医技资源映射实体
     */
    public DiagnosticResourceMap toDiagnosticResourceMapEntity(SaveDiagnosticResourceMapRequest request) {
        DiagnosticResourceMap entity = new DiagnosticResourceMap();
        entity.setId(request.getId());
        entity.setOrganizationId(request.getOrganizationId());
        entity.setActivityDefinitionId(request.getActivityDefinitionId());
        entity.setServiceCategoryCode(request.getServiceCategoryCode());
        entity.setScheduleId(request.getScheduleId());
        entity.setLocationId(request.getLocationId());
        entity.setDeviceId(request.getDeviceId());
        entity.setResourceName(request.getResourceName());
        if (request.getActiveFlag() == null) {
            entity.setActiveFlag(Boolean.TRUE);
        } else {
            entity.setActiveFlag(request.getActiveFlag());
        }
        return entity;
    }

    /**
     * 从可预约医嘱列表提取医嘱主键。
     *
     * @param rows 可预约医嘱 DTO 列表
     * @return 医嘱主键列表
     */
    public List<Long> collectServiceRequestIds(List<BookableServiceRequestDto> rows) {
        List<Long> ids = new ArrayList<>();
        if (rows == null) {
            return ids;
        }
        for (BookableServiceRequestDto row : rows) {
            if (row != null && row.getServiceRequestId() != null) {
                ids.add(row.getServiceRequestId());
            }
        }
        return ids;
    }

    /**
     * 按医嘱主键查找可预约医嘱。
     *
     * @param rows 可预约医嘱 DTO 列表
     * @param serviceRequestId 医嘱主键
     * @return 匹配的可预约医嘱 DTO，未找到时为 null
     */
    public BookableServiceRequestDto findServiceRequest(List<BookableServiceRequestDto> rows, Long serviceRequestId) {
        if (rows == null || serviceRequestId == null) {
            return null;
        }
        for (BookableServiceRequestDto row : rows) {
            if (serviceRequestId.equals(row.getServiceRequestId())) {
                return row;
            }
        }
        return null;
    }

    /**
     * 可预约医嘱列表 → 医嘱主键索引。
     *
     * @param rows 可预约医嘱 DTO 列表
     * @return 医嘱主键 → 可预约医嘱 DTO 映射
     */
    public Map<Long, BookableServiceRequestDto> indexServiceRequests(List<BookableServiceRequestDto> rows) {
        Map<Long, BookableServiceRequestDto> map = new java.util.HashMap<>();
        if (rows == null) {
            return map;
        }
        for (BookableServiceRequestDto row : rows) {
            if (row != null && row.getServiceRequestId() != null) {
                map.put(row.getServiceRequestId(), row);
            }
        }
        return map;
    }

    private List<OptionDto> toOptionDtos(List<MasterDataOption> options) {
        List<OptionDto> rows = new ArrayList<>();
        if (options == null) {
            return rows;
        }
        for (MasterDataOption option : options) {
            OptionDto dto = new OptionDto();
            dto.setValue(option.getValue());
            dto.setLabel(option.getLabel());
            rows.add(dto);
        }
        return rows;
    }
}
