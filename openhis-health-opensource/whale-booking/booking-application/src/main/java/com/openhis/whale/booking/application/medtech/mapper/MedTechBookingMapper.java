package com.openhis.whale.booking.application.medtech.mapper;

import com.openhis.whale.booking.application.medtech.request.CancelDiagnosticBookingRequest;
import com.openhis.whale.booking.application.medtech.request.CreateDiagnosticBookingRequest;
import com.openhis.whale.booking.application.medtech.request.RescheduleDiagnosticBookingRequest;
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
import com.openhis.whale.booking.contracts.dto.BookableServiceRequestDto;
import com.openhis.whale.booking.contracts.dto.EncounterSearchResultDto;
import com.openhis.whale.booking.contracts.dto.MasterDataOption;
import com.openhis.whale.booking.contracts.dto.ScheduleFormMasterDataSnapshot;
import com.openhis.whale.booking.contracts.query.SearchEncounterQuery;
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

    public MedTechBookingMapper(BookingRegisterMapper bookingRegisterMapper) {
        this.bookingRegisterMapper = bookingRegisterMapper;
    }

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

    public SearchEncounterQuery toSearchEncounterQuery(SearchEncounterRequest request) {
        SearchEncounterQuery query = new SearchEncounterQuery();
        query.setOrganizationId(request.getOrganizationId());
        query.setKeyword(request.getKeyword());
        query.setEncounterBusNo(request.getEncounterBusNo());
        query.setPatientId(request.getPatientId());
        return query;
    }

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

    public AllocateBookingCommand toAllocateBookingCommand(Long slotId, Long operatorId) {
        return bookingRegisterMapper.toAllocateBookingCommand(slotId, operatorId);
    }

    public ReleaseBookingCommand toReleaseBookingCommand(Long slotId, Long operatorId) {
        return bookingRegisterMapper.toReleaseBookingCommand(slotId, operatorId);
    }

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

    public CreateDiagnosticBookingResponse toCreateDiagnosticBookingResponse(Appointment appointment) {
        CreateDiagnosticBookingResponse response = new CreateDiagnosticBookingResponse();
        response.setAppointmentId(appointment.getId());
        response.setBusNo(appointment.getBusNo());
        return response;
    }

    public CancelAppointmentCommand toCancelAppointmentCommand(CancelDiagnosticBookingRequest request, Long operatorId) {
        CancelAppointmentCommand command = new CancelAppointmentCommand();
        command.setOperatorId(operatorId);
        CancelAppointmentCommand.CancelAppointmentItemCommand item = new CancelAppointmentCommand.CancelAppointmentItemCommand();
        item.setAppointmentId(request.getAppointmentId());
        item.setReason(request.getReason());
        command.setItem(item);
        return command;
    }

    public RescheduleDiagnosticBookingResponse toRescheduleDiagnosticBookingResponse(
        Long appointmentId,
        Long newSlotId) {
        RescheduleDiagnosticBookingResponse response = new RescheduleDiagnosticBookingResponse();
        response.setAppointmentId(appointmentId);
        response.setNewSlotId(newSlotId);
        return response;
    }

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
        entity.setActiveFlag(request.getActiveFlag() == null ? Boolean.TRUE : request.getActiveFlag());
        return entity;
    }

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
