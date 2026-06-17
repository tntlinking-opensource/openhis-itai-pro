package com.openhis.whale.booking.application.medtech;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.application.medtech.mapper.MedTechBookingMapper;
import com.openhis.whale.booking.application.medtech.request.CancelDiagnosticBookingRequest;
import com.openhis.whale.booking.application.medtech.request.CheckInDiagnosticRequest;
import com.openhis.whale.booking.application.medtech.request.CreateDiagnosticBookingRequest;
import com.openhis.whale.booking.application.medtech.request.ListBookableServiceRequestsRequest;
import com.openhis.whale.booking.application.medtech.request.ListDiagnosticBookableSlotsRequest;
import com.openhis.whale.booking.application.medtech.request.LoadMedTechFormInitRequest;
import com.openhis.whale.booking.application.medtech.request.PageDiagnosticBookingsRequest;
import com.openhis.whale.booking.application.medtech.request.PageDiagnosticResourceMapRequest;
import com.openhis.whale.booking.application.medtech.request.RescheduleDiagnosticBookingRequest;
import com.openhis.whale.booking.application.medtech.request.SaveDiagnosticResourceMapRequest;
import com.openhis.whale.booking.application.medtech.request.SearchEncounterRequest;
import com.openhis.whale.booking.application.medtech.response.CheckInDiagnosticResponse;
import com.openhis.whale.booking.application.medtech.response.CreateDiagnosticBookingResponse;
import com.openhis.whale.booking.application.medtech.response.DiagnosticResourceMapDto;
import com.openhis.whale.booking.application.medtech.response.EncounterContextDto;
import com.openhis.whale.booking.application.medtech.response.ListBookableServiceRequestsResponse;
import com.openhis.whale.booking.application.medtech.response.ListDiagnosticBookableSlotsResponse;
import com.openhis.whale.booking.application.medtech.response.LoadMedTechFormInitResponse;
import com.openhis.whale.booking.application.medtech.response.PageDiagnosticBookingsResponse;
import com.openhis.whale.booking.application.medtech.response.PageDiagnosticResourceMapResponse;
import com.openhis.whale.booking.application.medtech.response.RescheduleDiagnosticBookingResponse;
import com.openhis.whale.booking.application.medtech.response.SaveDiagnosticResourceMapResponse;
import com.openhis.whale.booking.application.medtech.response.ServiceRequestItemDto;
import com.openhis.whale.booking.application.register.mapper.BookingRegisterMapper;
import com.openhis.whale.booking.application.register.response.ListBookableSlotsResponse;
import com.openhis.whale.booking.application.support.BookingOperatorResolver;
import com.openhis.whale.booking.application.support.BookingPagedResultBuilder;
import com.openhis.whale.booking.contracts.dto.BookableServiceRequestDto;
import com.openhis.whale.booking.contracts.port.BookingEncounterPort;
import com.openhis.whale.booking.contracts.port.BookingLocationPort;
import com.openhis.whale.booking.contracts.port.BookingScheduleMasterDataPort;
import com.openhis.whale.booking.contracts.port.BookingServiceRequestPort;
import com.openhis.whale.booking.domain.appointment.entity.Appointment;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentBookingDetailModel;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentBookingListModel;
import com.openhis.whale.booking.domain.appointment.service.AppointmentDomainService;
import com.openhis.whale.booking.domain.appointment.service.AppointmentQueryService;
import com.openhis.whale.booking.domain.diagnostic.entity.DiagnosticResourceMap;
import com.openhis.whale.booking.domain.diagnostic.service.DiagnosticResourceMapDomainService;
import com.openhis.whale.booking.domain.diagnostic.service.DiagnosticResourceMapQueryService;
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
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 医技预约应用服务。
 *
 * <p>接入：POST /api/app/medTechBooking/{methodName}</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class MedTechBookingAppService {

    private final BookingEncounterPort bookingEncounterPort;
    private final BookingServiceRequestPort bookingServiceRequestPort;
    private final BookingLocationPort bookingLocationPort;
    private final BookingScheduleMasterDataPort bookingScheduleMasterDataPort;
    private final DiagnosticResourceMapQueryService diagnosticResourceMapQueryService;
    private final DiagnosticResourceMapDomainService diagnosticResourceMapDomainService;
    private final SlotQueryService slotQueryService;
    private final SlotDomainService slotDomainService;
    private final AppointmentDomainService appointmentDomainService;
    private final AppointmentQueryService appointmentQueryService;
    private final BookingRegisterMapper bookingRegisterMapper;
    private final MedTechBookingMapper medTechBookingMapper;
    private final BookingOperatorResolver bookingOperatorResolver;

    /**
     * 医技预约台初始化（7.1）。
     */
    public LoadMedTechFormInitResponse loadMedTechFormInit(LoadMedTechFormInitRequest request) {
        Long organizationId = request == null ? null : request.getOrganizationId();
        return medTechBookingMapper.toLoadMedTechFormInitResponse(
            bookingLocationPort.listActiveLocationsByOrganization(organizationId),
            bookingScheduleMasterDataPort.loadOutpatientScheduleFormOptions(organizationId));
    }

    /**
     * 检索就诊上下文。
     */
    public EncounterContextDto searchEncounter(@Valid SearchEncounterRequest request) {
        return bookingEncounterPort.searchEncounter(medTechBookingMapper.toSearchEncounterQuery(request))
            .map(medTechBookingMapper::toEncounterContextDto)
            .orElse(null);
    }

    /**
     * 按就诊列出可预约医嘱。
     */
    public ListBookableServiceRequestsResponse listBookableServiceRequests(
        @Valid ListBookableServiceRequestsRequest request) {
        List<BookableServiceRequestDto> rows =
            bookingServiceRequestPort.listBookableServiceRequestsByEncounter(request.getEncounterId());
        Map<Long, Long> appointmentIds = appointmentQueryService.mapActiveDiagnosticAppointmentIdsByServiceRequestIds(
            medTechBookingMapper.collectServiceRequestIds(rows));
        List<ServiceRequestItemDto> items = new ArrayList<>();
        for (BookableServiceRequestDto row : rows) {
            items.add(medTechBookingMapper.toServiceRequestItemDto(row, appointmentIds));
        }
        ListBookableServiceRequestsResponse response = new ListBookableServiceRequestsResponse();
        response.setItems(items);
        return response;
    }

    /**
     * 按医嘱解析 Schedule 并查询可约号源。
     */
    public ListDiagnosticBookableSlotsResponse listDiagnosticBookableSlots(
        @Valid ListDiagnosticBookableSlotsRequest request) {
        DiagnosticResourceMap resourceMap = this.resolveResourceMap(request.getOrganizationId(), request.getServiceRequestId());
        if (resourceMap.getScheduleId() == null) {
            ListDiagnosticBookableSlotsResponse empty = new ListDiagnosticBookableSlotsResponse();
            empty.setSlots(new ArrayList<>());
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
        return medTechBookingMapper.toListDiagnosticBookableSlotsResponse(slotsResponse);
    }

    /**
     * 创建医技预约：占号 + 写 Appointment + 关联医嘱。
     */
    @Transactional
    public CreateDiagnosticBookingResponse createDiagnosticBooking(@Valid CreateDiagnosticBookingRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        if (appointmentQueryService.findActiveDiagnosticBookingByServiceRequestId(request.getServiceRequestId()).isPresent()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "该医嘱已存在有效预约");
        }
        Long encounterId = bookingServiceRequestPort.resolveEncounterIdByServiceRequestId(request.getServiceRequestId());
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "无法解析医嘱所属就诊");
        }
        List<BookableServiceRequestDto> serviceRequests =
            bookingServiceRequestPort.listBookableServiceRequestsByEncounter(encounterId);
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
        slotDomainService.allocateBooking(medTechBookingMapper.toAllocateBookingCommand(request.getSlotId(), operatorId));
        Appointment appointment = appointmentDomainService.create(
            medTechBookingMapper.toCreateDiagnosticAppointmentCommand(
                request, slot, resourceMap, encounterId, operatorId));
        bookingServiceRequestPort.markServiceRequestBooked(request.getServiceRequestId(), appointment.getId());
        return medTechBookingMapper.toCreateDiagnosticBookingResponse(appointment);
    }

    /**
     * 取消医技预约并释放号源。
     */
    @Transactional
    public void cancelDiagnosticBooking(@Valid CancelDiagnosticBookingRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        AppointmentBookingDetailModel booking = appointmentQueryService.loadDiagnosticBookingById(request.getAppointmentId());
        appointmentDomainService.cancelAppointment(
            medTechBookingMapper.toCancelAppointmentCommand(request, operatorId));
        if (booking.getSlotId() != null) {
            slotDomainService.releaseBooking(
                medTechBookingMapper.toReleaseBookingCommand(booking.getSlotId(), operatorId));
        }
    }

    /**
     * 医技改约（7.5）：释放旧号源、占用新号源并重建预约。
     */
    @Transactional
    public RescheduleDiagnosticBookingResponse rescheduleDiagnosticBooking(
        @Valid RescheduleDiagnosticBookingRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        AppointmentBookingDetailModel booking = appointmentQueryService.loadDiagnosticBookingById(request.getAppointmentId());
        SlotDetailModel newSlot = slotQueryService.loadSlotById(request.getNewSlotId());
        if (booking.getSlotId() != null) {
            slotDomainService.releaseBooking(
                medTechBookingMapper.toReleaseBookingCommand(booking.getSlotId(), operatorId));
        }
        slotDomainService.allocateBooking(
            medTechBookingMapper.toAllocateBookingCommand(request.getNewSlotId(), operatorId));
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
            bookingServiceRequestPort.markServiceRequestBooked(createRequest.getServiceRequestId(), appointment.getId());
        }
        return medTechBookingMapper.toRescheduleDiagnosticBookingResponse(appointment.getId(), request.getNewSlotId());
    }

    /**
     * 医技到检报到（7.6）。
     */
    @Transactional
    public CheckInDiagnosticResponse checkInDiagnostic(@Valid CheckInDiagnosticRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
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
        Appointment queueItem = appointmentDomainService.checkInToQueue(
            medTechBookingMapper.toCheckInToQueueCommand(booking, destinationLocationId, operatorId));
        response.setAppointmentId(queueItem.getId());
        response.setDisplayOrder(queueItem.getDisplayOrder());
        return response;
    }

    /**
     * 医技资源映射分页（7.7）。
     */
    public PageDiagnosticResourceMapResponse pageDiagnosticResourceMap(
        @Valid PageDiagnosticResourceMapRequest request) {
        Page<DiagnosticResourceMap> page = diagnosticResourceMapQueryService.pageByOrganization(
            request.getOrganizationId(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<DiagnosticResourceMapDto> rows = new ArrayList<>();
        for (DiagnosticResourceMap entity : page.getContent()) {
            rows.add(medTechBookingMapper.toDiagnosticResourceMapDto(entity));
        }
        return BookingPagedResultBuilder.build(new PageDiagnosticResourceMapResponse(), rows, page);
    }

    /**
     * 保存医技资源映射（7.7）。
     */
    @Transactional
    public SaveDiagnosticResourceMapResponse saveDiagnosticResourceMap(@Valid SaveDiagnosticResourceMapRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        DiagnosticResourceMap saved = diagnosticResourceMapDomainService.save(
            medTechBookingMapper.toDiagnosticResourceMapEntity(request), operatorId);
        SaveDiagnosticResourceMapResponse response = new SaveDiagnosticResourceMapResponse();
        response.setId(saved.getId());
        return response;
    }

    /**
     * 医技预约分页：按就诊时返回医嘱维度，否则返回预约维度。
     */
    public PageDiagnosticBookingsResponse pageDiagnosticBookings(@Valid PageDiagnosticBookingsRequest request) {
        if (request.getEncounterId() != null) {
            return this.pageDiagnosticBookingsByEncounter(request);
        }
        Page<AppointmentBookingListModel> page = appointmentQueryService.pageDiagnosticBookings(
            request.getOrganizationId(), null, request.getPageNo(), request.getPageSize());
        List<ServiceRequestItemDto> rows = new ArrayList<>();
        for (AppointmentBookingListModel model : page.getContent()) {
            rows.add(medTechBookingMapper.toServiceRequestItemDtoFromAppointment(model));
        }
        return BookingPagedResultBuilder.build(new PageDiagnosticBookingsResponse(), rows, page);
    }

    private PageDiagnosticBookingsResponse pageDiagnosticBookingsByEncounter(PageDiagnosticBookingsRequest request) {
        List<BookableServiceRequestDto> allRows =
            bookingServiceRequestPort.listBookableServiceRequestsByEncounter(request.getEncounterId());
        Map<Long, Long> appointmentIds = appointmentQueryService.mapActiveDiagnosticAppointmentIdsByServiceRequestIds(
            medTechBookingMapper.collectServiceRequestIds(allRows));
        List<ServiceRequestItemDto> allItems = new ArrayList<>();
        for (BookableServiceRequestDto row : allRows) {
            allItems.add(medTechBookingMapper.toServiceRequestItemDto(row, appointmentIds));
        }
        int fromIndex = Math.max(0, (request.getPageNo() - 1) * request.getPageSize());
        int toIndex = Math.min(allItems.size(), fromIndex + request.getPageSize());
        List<ServiceRequestItemDto> pageItems = fromIndex >= allItems.size()
            ? new ArrayList<>()
            : new ArrayList<>(allItems.subList(fromIndex, toIndex));
        PageDiagnosticBookingsResponse response = new PageDiagnosticBookingsResponse();
        response.setContent(pageItems);
        response.setTotalCount((long) allItems.size());
        response.setPageNo(request.getPageNo());
        response.setPageSize(request.getPageSize());
        return response;
    }

    private DiagnosticResourceMap resolveResourceMap(Long organizationId, Long serviceRequestId) {
        Long activityId = bookingServiceRequestPort.resolveActivityIdByServiceRequestId(serviceRequestId);
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
