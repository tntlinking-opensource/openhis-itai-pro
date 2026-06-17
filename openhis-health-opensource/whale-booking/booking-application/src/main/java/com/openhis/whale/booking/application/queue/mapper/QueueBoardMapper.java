package com.openhis.whale.booking.application.queue.mapper;

import com.openhis.whale.booking.application.queue.request.ChangeOverRequest;
import com.openhis.whale.booking.application.queue.request.CheckInRequest;
import com.openhis.whale.booking.application.queue.request.DragChangeOrderRequest;
import com.openhis.whale.booking.application.queue.request.GetQueueDisplaySnapshotRequest;
import com.openhis.whale.booking.application.queue.request.PriorityTreatmentRequest;
import com.openhis.whale.booking.application.queue.response.CallToSeeResponse;
import com.openhis.whale.booking.application.queue.response.CheckInResponse;
import com.openhis.whale.booking.application.queue.response.QueueDisplaySnapshotResponse;
import com.openhis.whale.booking.application.queue.response.QueueListItemDto;
import com.openhis.whale.booking.domain.appointment.command.CallToSeeCommand;
import com.openhis.whale.booking.domain.appointment.command.ChangeOverCommand;
import com.openhis.whale.booking.domain.appointment.command.CheckInOutpatientBookingCommand;
import com.openhis.whale.booking.domain.appointment.command.CheckInToQueueCommand;
import com.openhis.whale.booking.domain.appointment.command.CreateAppointmentCommand;
import com.openhis.whale.booking.domain.appointment.command.DragChangeOrderCommand;
import com.openhis.whale.booking.domain.appointment.command.NoShowCommand;
import com.openhis.whale.booking.domain.appointment.command.PriorityTreatmentCommand;
import com.openhis.whale.booking.domain.appointment.entity.Appointment;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentQueuePageModel;
import com.openhis.whale.booking.domain.queue.entity.QueueDisplayConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 候诊叫号应用层映射器。
 */
@Component
public class QueueBoardMapper {

    private static final String CHECK_IN_ONLY_KIND = "CHECK_IN_ONLY";
    private static final String DEFAULT_ORIGIN_CHANNEL = "WINDOW";

    /**
     * 预约报到入队命令。
     */
    public CheckInOutpatientBookingCommand toCheckInOutpatientBookingCommand(CheckInRequest request, Long operatorId) {
        CheckInOutpatientBookingCommand command = new CheckInOutpatientBookingCommand();
        command.setOperatorId(operatorId);
        CheckInOutpatientBookingCommand.CheckInOutpatientBookingItemCommand item =
            new CheckInOutpatientBookingCommand.CheckInOutpatientBookingItemCommand();
        item.setAppointmentId(request.getAppointmentId());
        item.setOrganizationId(request.getOrganizationId());
        item.setDestinationLocationId(request.getDestinationLocationId());
        command.setItem(item);
        return command;
    }

    /**
     * 现场报到入队命令。
     */
    public CheckInToQueueCommand toCheckInToQueueCommand(CheckInRequest request, Long operatorId) {
        CheckInToQueueCommand command = new CheckInToQueueCommand();
        command.setOperatorId(operatorId);
        CheckInToQueueCommand.CheckInToQueueItemCommand item = new CheckInToQueueCommand.CheckInToQueueItemCommand();
        item.setAppointmentKind(CHECK_IN_ONLY_KIND);
        item.setOrganizationId(request.getOrganizationId());
        item.setDestinationLocationId(request.getDestinationLocationId());
        item.setEncounterId(request.getEncounterId());
        item.setPatientId(request.getPatientId());
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
     * 叫号命令。
     */
    public CallToSeeCommand toCallToSeeCommand(Long appointmentId, Long practitionerId, Long operatorId) {
        CallToSeeCommand command = new CallToSeeCommand();
        command.setOperatorId(operatorId);
        CallToSeeCommand.CallToSeeItemCommand item = new CallToSeeCommand.CallToSeeItemCommand();
        item.setAppointmentId(appointmentId);
        item.setPractitionerId(practitionerId);
        command.setItem(item);
        return command;
    }

    /**
     * 过号命令。
     */
    public NoShowCommand toNoShowCommand(Long appointmentId, String reasonCode, Long operatorId) {
        NoShowCommand command = new NoShowCommand();
        command.setOperatorId(operatorId);
        NoShowCommand.NoShowItemCommand item = new NoShowCommand.NoShowItemCommand();
        item.setAppointmentId(appointmentId);
        item.setReasonCode(reasonCode);
        command.setItem(item);
        return command;
    }

    /**
     * 报到响应。
     */
    public CheckInResponse toCheckInResponse(Appointment appointment) {
        CheckInResponse response = new CheckInResponse();
        response.setAppointmentId(appointment.getId());
        response.setBusNo(appointment.getBusNo());
        response.setDisplayOrder(appointment.getDisplayOrder());
        response.setAppointmentKind(appointment.getAppointmentKind());
        return response;
    }

    /**
     * 叫号响应（叫号前快照 + 叫号时间）。
     */
    public CallToSeeResponse toCallToSeeResponse(
        AppointmentQueuePageModel snapshot,
        String patientName,
        Long practitionerId,
        java.time.LocalDateTime calledAt) {
        CallToSeeResponse response = new CallToSeeResponse();
        response.setAppointmentId(snapshot.getId());
        response.setBusNo(snapshot.getBusNo());
        response.setPatientId(snapshot.getPatientId());
        response.setPatientName(patientName);
        response.setDisplayOrder(snapshot.getDisplayOrder());
        response.setCalledAt(calledAt);
        response.setPractitionerId(practitionerId);
        return response;
    }

    /**
     * 队列列表行。
     */
    public QueueListItemDto toQueueListItemDto(AppointmentQueuePageModel model, Map<Long, String> patientNames) {
        QueueListItemDto dto = new QueueListItemDto();
        dto.setAppointmentId(model.getId());
        dto.setBusNo(model.getBusNo());
        dto.setPatientId(model.getPatientId());
        if (model.getPatientId() != null && patientNames != null) {
            dto.setPatientName(patientNames.get(model.getPatientId()));
        }
        dto.setEncounterId(model.getEncounterId());
        dto.setDisplayOrder(model.getDisplayOrder());
        dto.setTriageLevel(model.getTriageLevel());
        dto.setAppointmentKind(model.getAppointmentKind());
        dto.setStartInstant(model.getStartInstant());
        dto.setStatusView(model.getStatusView());
        return dto;
    }

    public PriorityTreatmentCommand toPriorityTreatmentCommand(PriorityTreatmentRequest request, Long operatorId) {
        PriorityTreatmentCommand command = new PriorityTreatmentCommand();
        command.setOperatorId(operatorId);
        PriorityTreatmentCommand.PriorityTreatmentItemCommand item =
            new PriorityTreatmentCommand.PriorityTreatmentItemCommand();
        item.setAppointmentId(request.getAppointmentId());
        item.setDestinationLocationId(request.getDestinationLocationId());
        command.setItem(item);
        return command;
    }

    public ChangeOverCommand toChangeOverCommand(ChangeOverRequest request, Long operatorId) {
        ChangeOverCommand command = new ChangeOverCommand();
        command.setOperatorId(operatorId);
        ChangeOverCommand.ChangeOverItemCommand item = new ChangeOverCommand.ChangeOverItemCommand();
        item.setAppointmentId(request.getAppointmentId());
        item.setMoveForward(request.getMoveForward());
        command.setItem(item);
        return command;
    }

    public DragChangeOrderCommand toDragChangeOrderCommand(DragChangeOrderRequest request, Long operatorId) {
        DragChangeOrderCommand command = new DragChangeOrderCommand();
        command.setOperatorId(operatorId);
        DragChangeOrderCommand.DragChangeOrderItemCommand item = new DragChangeOrderCommand.DragChangeOrderItemCommand();
        item.setAppointmentId(request.getAppointmentId());
        item.setTargetIndex(request.getTargetIndex());
        command.setItem(item);
        return command;
    }

    public QueueDisplaySnapshotResponse toQueueDisplaySnapshotResponse(
        QueueDisplayConfig config,
        List<AppointmentQueuePageModel> snapshot,
        Map<Long, String> patientNames) {
        QueueDisplaySnapshotResponse response = new QueueDisplaySnapshotResponse();
        if (config != null) {
            response.setDisplayTitle(config.getDisplayTitle());
            response.setRefreshSeconds(config.getRefreshSeconds());
        } else {
            response.setRefreshSeconds(5);
        }
        List<QueueListItemDto> waiting = new ArrayList<>();
        CallToSeeResponse currentCall = null;
        for (AppointmentQueuePageModel model : snapshot) {
            QueueListItemDto row = this.toQueueListItemDto(model, patientNames);
            if (model.getCalledAt() != null && currentCall == null) {
                currentCall = new CallToSeeResponse();
                currentCall.setAppointmentId(model.getId());
                currentCall.setBusNo(model.getBusNo());
                currentCall.setPatientId(model.getPatientId());
                currentCall.setPatientName(row.getPatientName());
                currentCall.setDisplayOrder(model.getDisplayOrder());
                currentCall.setCalledAt(model.getCalledAt());
            } else {
                waiting.add(row);
            }
        }
        response.setCurrentCall(currentCall);
        response.setWaitingList(waiting);
        return response;
    }
}
