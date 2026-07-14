/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.queue.mapper;

import com.openhis.whale.booking.application.queue.request.ChangeOverRequest;
import com.openhis.whale.booking.application.queue.request.CheckInRequest;
import com.openhis.whale.booking.application.queue.request.DragChangeOrderRequest;
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
 * <p>职责：报到/叫号/队列调序/叫号屏快照的 Request/Command/DTO 转换；不含业务门禁与事务。</p>
 */
@Component
public class QueueBoardMapper {

    private static final String CHECK_IN_ONLY_KIND = "CHECK_IN_ONLY";
    private static final String DEFAULT_ORIGIN_CHANNEL = "WINDOW";

    /**
     * 预约报到入队命令。
     *
     * @param request 报到请求
     * @param operatorId 操作人主键
     * @return 门诊预约报到命令
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
     *
     * @param request 报到请求
     * @param operatorId 操作人主键
     * @return 现场报到入队命令
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
     *
     * @param appointmentId 预约主键
     * @param practitionerId 医师主键
     * @param operatorId 操作人主键
     * @return 叫号命令
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
     *
     * @param appointmentId 预约主键
     * @param reasonCode 过号原因编码
     * @param operatorId 操作人主键
     * @return 过号命令
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
     *
     * @param appointment 预约实体
     * @return 报到响应
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
     *
     * @param snapshot 队列分页模型快照
     * @param patientName 患者姓名
     * @param practitionerId 医师主键
     * @param calledAt 叫号时间
     * @return 叫号响应
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
     *
     * @param model 队列分页模型
     * @param patientNames 患者主键 → 姓名映射
     * @return 队列列表行 DTO
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

    /**
     * 优先就诊请求 → 领域命令。
     *
     * @param request 优先就诊请求
     * @param operatorId 操作人主键
     * @return 优先就诊命令
     */
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

    /**
     * 过号顺延请求 → 领域命令。
     *
     * @param request 过号顺延请求
     * @param operatorId 操作人主键
     * @return 过号顺延命令
     */
    public ChangeOverCommand toChangeOverCommand(ChangeOverRequest request, Long operatorId) {
        ChangeOverCommand command = new ChangeOverCommand();
        command.setOperatorId(operatorId);
        ChangeOverCommand.ChangeOverItemCommand item = new ChangeOverCommand.ChangeOverItemCommand();
        item.setAppointmentId(request.getAppointmentId());
        item.setMoveForward(request.getMoveForward());
        command.setItem(item);
        return command;
    }

    /**
     * 拖拽调序请求 → 领域命令。
     *
     * @param request 拖拽调序请求
     * @param operatorId 操作人主键
     * @return 拖拽调序命令
     */
    public DragChangeOrderCommand toDragChangeOrderCommand(DragChangeOrderRequest request, Long operatorId) {
        DragChangeOrderCommand command = new DragChangeOrderCommand();
        command.setOperatorId(operatorId);
        DragChangeOrderCommand.DragChangeOrderItemCommand item = new DragChangeOrderCommand.DragChangeOrderItemCommand();
        item.setAppointmentId(request.getAppointmentId());
        item.setTargetIndex(request.getTargetIndex());
        command.setItem(item);
        return command;
    }

    /**
     * 叫号屏配置 + 队列快照 → 展示响应（分离当前叫号与等候列表）。
     *
     * @param config 叫号屏展示配置
     * @param snapshot 队列快照列表
     * @param patientNames 患者主键 → 姓名映射
     * @return 叫号屏展示快照响应
     */
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
