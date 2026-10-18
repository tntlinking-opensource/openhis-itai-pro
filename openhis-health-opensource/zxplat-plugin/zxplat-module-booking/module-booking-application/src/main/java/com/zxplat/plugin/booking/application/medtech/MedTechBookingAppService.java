/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.medtech;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.plugin.booking.application.medtech.mapper.MedTechBookingMapper;
import com.zxplat.plugin.booking.application.medtech.request.CancelDiagnosticBookingRequest;
import com.zxplat.plugin.booking.application.medtech.request.CheckInDiagnosticRequest;
import com.zxplat.plugin.booking.application.medtech.request.CreateDiagnosticBookingRequest;
import com.zxplat.plugin.booking.application.medtech.request.ListBookableServiceRequestsRequest;
import com.zxplat.plugin.booking.application.medtech.request.ListDiagnosticBookableSlotsRequest;
import com.zxplat.plugin.booking.application.medtech.request.LoadMedTechFormInitRequest;
import com.zxplat.plugin.booking.application.medtech.request.PageDiagnosticBookingsRequest;
import com.zxplat.plugin.booking.application.medtech.request.PageDiagnosticResourceMapRequest;
import com.zxplat.plugin.booking.application.medtech.request.RescheduleDiagnosticBookingRequest;
import com.zxplat.plugin.booking.application.medtech.request.SaveDiagnosticResourceMapRequest;
import com.zxplat.plugin.booking.application.medtech.request.SearchEncounterRequest;
import com.zxplat.plugin.booking.application.medtech.response.CheckInDiagnosticResponse;
import com.zxplat.plugin.booking.application.medtech.response.CreateDiagnosticBookingResponse;
import com.zxplat.plugin.booking.application.medtech.response.DiagnosticResourceMapDto;
import com.zxplat.plugin.booking.application.medtech.response.EncounterContextDto;
import com.zxplat.plugin.booking.application.medtech.response.ListBookableServiceRequestsResponse;
import com.zxplat.plugin.booking.application.medtech.response.ListDiagnosticBookableSlotsResponse;
import com.zxplat.plugin.booking.application.medtech.response.LoadMedTechFormInitResponse;
import com.zxplat.plugin.booking.application.medtech.response.PageDiagnosticBookingsResponse;
import com.zxplat.plugin.booking.application.medtech.response.PageDiagnosticResourceMapResponse;
import com.zxplat.plugin.booking.application.medtech.response.RescheduleDiagnosticBookingResponse;
import com.zxplat.plugin.booking.application.medtech.response.SaveDiagnosticResourceMapResponse;
import com.zxplat.plugin.booking.application.medtech.response.ServiceRequestItemDto;
import com.zxplat.plugin.booking.application.register.mapper.BookingRegisterMapper;
import com.zxplat.plugin.booking.application.register.response.ListBookableSlotsResponse;
import com.zxplat.plugin.booking.application.share.BookingOperatorResolver;
import com.zxplat.plugin.booking.application.share.BookingPagedResultBuilder;
import com.zxplat.plugin.booking.domain.medtech.result.BookableServiceRequestDto;
import com.zxplat.plugin.booking.domain.masterdata.service.BookingEncounterQueryService;
import com.zxplat.plugin.booking.domain.masterdata.service.BookingLocationQueryService;
import com.zxplat.plugin.booking.domain.masterdata.service.BookingScheduleMasterDataQueryService;
import com.zxplat.plugin.booking.domain.masterdata.service.BookingServiceRequestQueryService;
import com.zxplat.plugin.booking.domain.medtech.service.BookingServiceRequestCommandService;
import com.zxplat.plugin.booking.domain.appointment.entity.Appointment;
import com.zxplat.plugin.booking.domain.appointment.queries.model.AppointmentBookingDetailModel;
import com.zxplat.plugin.booking.domain.appointment.queries.model.AppointmentBookingListModel;
import com.zxplat.plugin.booking.domain.appointment.service.AppointmentDomainService;
import com.zxplat.plugin.booking.domain.appointment.service.AppointmentQueryService;
import com.zxplat.plugin.booking.domain.diagnostic.entity.DiagnosticResourceMap;
import com.zxplat.plugin.booking.domain.diagnostic.service.DiagnosticResourceMapDomainService;
import com.zxplat.plugin.booking.domain.diagnostic.service.DiagnosticResourceMapQueryService;
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
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 医技预约应用服务（7.x）。
 *
 * <p>职责边界：医技可约号源、预约创建/改约/取消/到检、资源映射维护；医嘱状态经 Reader/Writer/Client 回写。</p>
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/medTechBooking/{methodName}}。</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class MedTechBookingAppService {

    /** 就诊检索（health 经 QueryService/CommandService）。 */
    private final BookingEncounterQueryService bookingEncounterQueryService;
    /** 医嘱查询。 */
    private final BookingServiceRequestQueryService bookingServiceRequestQueryService;
    /** 医嘱预约回写。 */
    private final BookingServiceRequestCommandService bookingServiceRequestCommandService;
    /** 科室/位置主数据。 */
    private final BookingLocationQueryService bookingLocationQueryService;
    /** 排班主数据：表单选项。 */
    private final BookingScheduleMasterDataQueryService bookingScheduleMasterDataQueryService;
    /** 医技资源映射读侧。 */
    private final DiagnosticResourceMapQueryService diagnosticResourceMapQueryService;
    /** 医技资源映射写侧。 */
    private final DiagnosticResourceMapDomainService diagnosticResourceMapDomainService;
    /** 号源读侧。 */
    private final SlotQueryService slotQueryService;
    /** 号源写侧：allocate/release。 */
    private final SlotDomainService slotDomainService;
    /** 预约写侧。 */
    private final AppointmentDomainService appointmentDomainService;
    /** 预约读侧。 */
    private final AppointmentQueryService appointmentQueryService;
    /** 可约号源 DTO 映射（复用门诊）。 */
    private final BookingRegisterMapper bookingRegisterMapper;
    /** 医技 Request/Command/DTO 映射。 */
    private final MedTechBookingMapper medTechBookingMapper;
    /** 写路径操作人。 */
    private final BookingOperatorResolver bookingOperatorResolver;

    /**
     * 医技预约台初始化（7.1）。
     */
    public LoadMedTechFormInitResponse loadMedTechFormInit(LoadMedTechFormInitRequest request) {
        // Pre：解析机构并加载科室与排班主数据选项。
        Long organizationId;
        if (request == null) {
            organizationId = null;
        } else {
            organizationId = request.getOrganizationId();
        }
        // Asm：映射医技预约台初始化响应。
        return medTechBookingMapper.toLoadMedTechFormInitResponse(
            bookingLocationQueryService.listActiveLocationsByOrganization(organizationId),
            bookingScheduleMasterDataQueryService.loadOutpatientScheduleFormOptions(organizationId));
    }

    /**
     * 检索就诊上下文。
     */
    public EncounterContextDto searchEncounter(@Valid SearchEncounterRequest request) {
        // Pre：委托就诊 Reader 检索就诊上下文。
        // Asm：映射就诊上下文 DTO；未找到时返回 null。
        return bookingEncounterQueryService.searchEncounter(medTechBookingMapper.toSearchEncounterQuery(request))
            .map(medTechBookingMapper::toEncounterContextDto)
            .orElse(null);
    }

    /**
     * 按就诊列出可预约医嘱。
     */
    public ListBookableServiceRequestsResponse listBookableServiceRequests(
        @Valid ListBookableServiceRequestsRequest request) {
        // Pre：按就诊加载可预约医嘱并关联已有预约主键。
        List<BookableServiceRequestDto> rows =
            bookingServiceRequestQueryService.listBookableServiceRequestsByEncounter(request.getEncounterId());
        Map<Long, Long> appointmentIds = appointmentQueryService.mapActiveDiagnosticAppointmentIdsByServiceRequestIds(
            medTechBookingMapper.collectServiceRequestIds(rows));
        List<ServiceRequestItemDto> items = new ArrayList<>();
        for (BookableServiceRequestDto row : rows) {
            items.add(medTechBookingMapper.toServiceRequestItemDto(row, appointmentIds));
        }
        ListBookableServiceRequestsResponse response = new ListBookableServiceRequestsResponse();
        response.setItems(items);
        // Asm：返回可预约医嘱列表响应。
        return response;
    }

    /**
     * 按医嘱解析 Schedule 并查询可约号源。
     */
    public ListDiagnosticBookableSlotsResponse listDiagnosticBookableSlots(
        @Valid ListDiagnosticBookableSlotsRequest request) {
        // Pre：解析医技资源映射并查询当日可约号源。
        DiagnosticResourceMap resourceMap = this.resolveResourceMap(request.getOrganizationId(), request.getServiceRequestId());
        if (resourceMap.getScheduleId() == null) {
            ListDiagnosticBookableSlotsResponse empty = new ListDiagnosticBookableSlotsResponse();
            empty.setSlots(new ArrayList<>());
            // Asm：无映射 Schedule 时返回空列表。
            return empty;
        }
        LocalDateTime dayStart = request.getDate().atStartOfDay();
        LocalDateTime dayEnd = request.getDate().plusDays(1).atStartOfDay();
        FindBookableSlotsQuery slotQuery = new FindBookableSlotsQuery();
        slotQuery.setScheduleId(resourceMap.getScheduleId());
        slotQuery.setStartTime(dayStart);
        slotQuery.setEndTime(dayEnd);
        List<BookableSlotModel> bookable = slotQueryService.findBookableSlots(slotQuery);
        List<BookableSlotModel> merged = new ArrayList<>();
        List<SlotDetailModel> slotDetails = new ArrayList<>();
        for (BookableSlotModel model : bookable) {
            if (Boolean.TRUE.equals(model.getIsFull())) {
                continue;
            }
            merged.add(model);
            slotDetails.add(slotQueryService.loadSlotById(model.getSlotId()));
        }
        ListBookableSlotsResponse slotsResponse = bookingRegisterMapper.toListBookableSlotsResponse(merged, slotDetails);
        // Asm：映射医技可约号源响应。
        return medTechBookingMapper.toListDiagnosticBookableSlotsResponse(slotsResponse);
    }

    /**
     * 创建医技预约：占号 + 写 Appointment + 关联医嘱。
     */
    public CreateDiagnosticBookingResponse createDiagnosticBooking(@Valid CreateDiagnosticBookingRequest request) {
        // Gate：写路径须登录；医嘱不可重复预约。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        if (appointmentQueryService.findActiveDiagnosticBookingByServiceRequestId(request.getServiceRequestId()).isPresent()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "该医嘱已存在有效预约");
        }
        // Pre：解析就诊、可预约医嘱与资源映射。
        Long encounterId = bookingServiceRequestQueryService.resolveEncounterIdByServiceRequestId(request.getServiceRequestId());
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "无法解析医嘱所属就诊");
        }
        List<BookableServiceRequestDto> serviceRequests =
            bookingServiceRequestQueryService.listBookableServiceRequestsByEncounter(encounterId);
        BookableServiceRequestDto serviceRequest = medTechBookingMapper.findServiceRequest(serviceRequests, request.getServiceRequestId());
        if (serviceRequest == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "医嘱不可预约或不存在");
        }
        DiagnosticResourceMap resourceMap = this.resolveResourceMap(request.getOrganizationId(), request.getServiceRequestId());
        SlotDetailModel slot = slotQueryService.loadSlotById(request.getSlotId());
        if (!request.getOrganizationId().equals(slot.getOrganizationId())) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "号源与机构不匹配");
        }
        if (resourceMap.getScheduleId() != null && !resourceMap.getScheduleId().equals(slot.getScheduleId())) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "号源与医技资源映射不匹配");
        }
        // Cmd：占号、建预约、标记医嘱已预约。
        slotDomainService.allocateBooking(medTechBookingMapper.toAllocateBookingCommand(request.getSlotId(), operatorId));
        Appointment appointment = appointmentDomainService.create(
            medTechBookingMapper.toCreateDiagnosticAppointmentCommand(
                request, slot, resourceMap, encounterId, operatorId));
        bookingServiceRequestCommandService.markServiceRequestBooked(request.getServiceRequestId(), appointment.getId());
        // Asm：映射创建响应。
        return medTechBookingMapper.toCreateDiagnosticBookingResponse(appointment);
    }

    /**
     * 取消医技预约并释放号源。
     */
    public void cancelDiagnosticBooking(@Valid CancelDiagnosticBookingRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：加载预约以获取 slotId。
        AppointmentBookingDetailModel booking = appointmentQueryService.loadDiagnosticBookingById(request.getAppointmentId());
        // Cmd：取消预约。
        appointmentDomainService.cancelAppointment(
            medTechBookingMapper.toCancelAppointmentCommand(request, operatorId));
        // Cmd：释放号源容量。
        if (booking.getSlotId() != null) {
            slotDomainService.releaseBooking(
                medTechBookingMapper.toReleaseBookingCommand(booking.getSlotId(), operatorId));
        }
    }

    /**
     * 医技改约（7.5）：释放旧号源、占用新号源并重建预约。
     */
    public RescheduleDiagnosticBookingResponse rescheduleDiagnosticBooking(
        @Valid RescheduleDiagnosticBookingRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：加载原预约与新号源。
        AppointmentBookingDetailModel booking = appointmentQueryService.loadDiagnosticBookingById(request.getAppointmentId());
        SlotDetailModel newSlot = slotQueryService.loadSlotById(request.getNewSlotId());
        // Cmd：释放旧号源、占用新号源。
        if (booking.getSlotId() != null) {
            slotDomainService.releaseBooking(
                medTechBookingMapper.toReleaseBookingCommand(booking.getSlotId(), operatorId));
        }
        slotDomainService.allocateBooking(
            medTechBookingMapper.toAllocateBookingCommand(request.getNewSlotId(), operatorId));
        // Cmd：取消旧预约并创建新预约。
        CancelDiagnosticBookingRequest cancelRequest = new CancelDiagnosticBookingRequest();
        cancelRequest.setAppointmentId(request.getAppointmentId());
        cancelRequest.setReason("改约释放");
        appointmentDomainService.cancelAppointment(
            medTechBookingMapper.toCancelAppointmentCommand(cancelRequest, operatorId));
        CreateDiagnosticBookingRequest createRequest = new CreateDiagnosticBookingRequest();
        createRequest.setOrganizationId(booking.getOrganizationId());
        createRequest.setPatientId(booking.getPatientId());
        if (booking.getAppointmentTypeCode() != null) {
            try {
                createRequest.setServiceRequestId(Long.valueOf(booking.getAppointmentTypeCode()));
            } catch (NumberFormatException ignored) {
                // 非医嘱关联预约
            }
        }
        createRequest.setSlotId(request.getNewSlotId());
        createRequest.setEncounterId(booking.getEncounterId());
        Long destinationLocationId = request.getDestinationLocationId();
        if (destinationLocationId == null) {
            destinationLocationId = booking.getDestinationLocationId();
        }
        DiagnosticResourceMap resourceMap = new DiagnosticResourceMap();
        resourceMap.setLocationId(destinationLocationId);
        resourceMap.setScheduleId(newSlot.getScheduleId());
        Appointment appointment = appointmentDomainService.create(
            medTechBookingMapper.toCreateDiagnosticAppointmentCommand(
                createRequest, newSlot, resourceMap, booking.getEncounterId(), operatorId));
        if (createRequest.getServiceRequestId() != null) {
            bookingServiceRequestCommandService.markServiceRequestBooked(createRequest.getServiceRequestId(), appointment.getId());
        }
        // Asm：映射改约响应。
        return medTechBookingMapper.toRescheduleDiagnosticBookingResponse(appointment.getId(), request.getNewSlotId());
    }

    /**
     * 医技到检报到（7.6）。
     */
    public CheckInDiagnosticResponse checkInDiagnostic(@Valid CheckInDiagnosticRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：加载医技预约；已报到则直接返回。
        AppointmentBookingDetailModel booking = appointmentQueryService.loadDiagnosticBookingById(request.getAppointmentId());
        CheckInDiagnosticResponse response = new CheckInDiagnosticResponse();
        if (booking.getDisplayOrder() != null) {
            response.setAppointmentId(booking.getId());
            response.setDisplayOrder(booking.getDisplayOrder());
            return response;
        }
        if (booking.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "医技到检报到需关联就诊");
        }
        Long destinationLocationId = request.getDestinationLocationId();
        if (destinationLocationId == null) {
            destinationLocationId = booking.getDestinationLocationId();
        }
        if (destinationLocationId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "到检报到 destinationLocationId 不能为空");
        }
        // Cmd：入队报到。
        Appointment queueItem = appointmentDomainService.checkInToQueue(
            medTechBookingMapper.toCheckInToQueueCommand(booking, destinationLocationId, operatorId));
        // Asm：映射报到响应。
        response.setAppointmentId(queueItem.getId());
        response.setDisplayOrder(queueItem.getDisplayOrder());
        return response;
    }

    /**
     * 医技资源映射分页（7.7）。
     */
    public PageDiagnosticResourceMapResponse pageDiagnosticResourceMap(
        @Valid PageDiagnosticResourceMapRequest request) {
        // Pre：按机构分页加载医技资源映射。
        Page<DiagnosticResourceMap> page = diagnosticResourceMapQueryService.pageByOrganization(
            request.getOrganizationId(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<DiagnosticResourceMapDto> rows = new ArrayList<>();
        for (DiagnosticResourceMap entity : page.getContent()) {
            rows.add(medTechBookingMapper.toDiagnosticResourceMapDto(entity));
        }
        // Asm：映射 DTO 并包装分页结果。
        return BookingPagedResultBuilder.build(new PageDiagnosticResourceMapResponse(), rows, page);
    }

    /**
     * 保存医技资源映射（7.7）。
     */
    public SaveDiagnosticResourceMapResponse saveDiagnosticResourceMap(@Valid SaveDiagnosticResourceMapRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：持久化医技资源映射。
        DiagnosticResourceMap saved = diagnosticResourceMapDomainService.save(
            medTechBookingMapper.toDiagnosticResourceMapEntity(request), operatorId);
        // Asm：返回主键。
        SaveDiagnosticResourceMapResponse response = new SaveDiagnosticResourceMapResponse();
        response.setId(saved.getId());
        return response;
    }

    /**
     * 医技预约分页：按就诊时返回医嘱维度，否则返回预约维度。
     */
    public PageDiagnosticBookingsResponse pageDiagnosticBookings(@Valid PageDiagnosticBookingsRequest request) {
        // Pre：按就诊或机构维度加载医技预约列表。
        if (request.getEncounterId() != null) {
            return this.pageDiagnosticBookingsByEncounter(request);
        }
        Page<AppointmentBookingListModel> page = appointmentQueryService.pageDiagnosticBookings(
            request.getOrganizationId(), null, request.getPageNo(), request.getPageSize());
        List<ServiceRequestItemDto> rows = new ArrayList<>();
        for (AppointmentBookingListModel model : page.getContent()) {
            rows.add(medTechBookingMapper.toServiceRequestItemDtoFromAppointment(model));
        }
        // Asm：映射预约维度分页结果。
        return BookingPagedResultBuilder.build(new PageDiagnosticBookingsResponse(), rows, page);
    }

    private PageDiagnosticBookingsResponse pageDiagnosticBookingsByEncounter(PageDiagnosticBookingsRequest request) {
        List<BookableServiceRequestDto> allRows =
            bookingServiceRequestQueryService.listBookableServiceRequestsByEncounter(request.getEncounterId());
        Map<Long, Long> appointmentIds = appointmentQueryService.mapActiveDiagnosticAppointmentIdsByServiceRequestIds(
            medTechBookingMapper.collectServiceRequestIds(allRows));
        List<ServiceRequestItemDto> allItems = new ArrayList<>();
        for (BookableServiceRequestDto row : allRows) {
            allItems.add(medTechBookingMapper.toServiceRequestItemDto(row, appointmentIds));
        }
        int fromIndex = Math.max(0, (request.getPageNo() - 1) * request.getPageSize());
        int toIndex = Math.min(allItems.size(), fromIndex + request.getPageSize());
        List<ServiceRequestItemDto> pageItems;
        if (fromIndex >= allItems.size()) {
            pageItems = new ArrayList<>();
        } else {
            pageItems = new ArrayList<>(allItems.subList(fromIndex, toIndex));
        }
        PageDiagnosticBookingsResponse response = new PageDiagnosticBookingsResponse();
        response.setContent(pageItems);
        response.setTotalCount((long) allItems.size());
        response.setPageNo(request.getPageNo());
        response.setPageSize(request.getPageSize());
        return response;
    }

    private DiagnosticResourceMap resolveResourceMap(Long organizationId, Long serviceRequestId) {
        Long activityId = bookingServiceRequestQueryService.resolveActivityIdByServiceRequestId(serviceRequestId);
        if (activityId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "无法解析医嘱关联项目");
        }
        DiagnosticResourceMap resourceMap = diagnosticResourceMapQueryService
            .findActiveByActivityDefinitionId(organizationId, activityId)
            .orElse(null);
        if (resourceMap == null || resourceMap.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_NOT_FOUND, "未配置医技资源映射或 Schedule");
        }
        return resourceMap;
    }
}
