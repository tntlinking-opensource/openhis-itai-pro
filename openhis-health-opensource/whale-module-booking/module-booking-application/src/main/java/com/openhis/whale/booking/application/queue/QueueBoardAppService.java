/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.queue;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.application.outpatient.mapper.OutpatientScheduleMapper;
import com.openhis.whale.booking.application.queue.mapper.QueueBoardMapper;
import com.openhis.whale.booking.application.queue.request.CallToSeeRequest;
import com.openhis.whale.booking.application.queue.request.CheckInRequest;
import com.openhis.whale.booking.application.queue.request.NoShowRequest;
import com.openhis.whale.booking.application.queue.request.ChangeOverRequest;
import com.openhis.whale.booking.application.queue.request.DragChangeOrderRequest;
import com.openhis.whale.booking.application.queue.request.GetQueueDisplaySnapshotRequest;
import com.openhis.whale.booking.application.queue.request.PriorityTreatmentRequest;
import com.openhis.whale.booking.application.queue.request.PageCompletedQueueRequest;
import com.openhis.whale.booking.application.queue.request.PageQueueRequest;
import com.openhis.whale.booking.application.queue.response.CallToSeeResponse;
import com.openhis.whale.booking.application.queue.response.CheckInResponse;
import com.openhis.whale.booking.application.queue.response.PageCompletedQueueResponse;
import com.openhis.whale.booking.application.queue.response.PageQueueResponse;
import com.openhis.whale.booking.application.queue.response.QueueDisplaySnapshotResponse;
import com.openhis.whale.booking.application.queue.response.QueueListItemDto;
import com.openhis.whale.booking.application.share.BookingAuditSupport;
import com.openhis.whale.booking.application.share.BookingOperatorResolver;
import com.openhis.whale.booking.application.share.BookingPagedResultBuilder;
import com.openhis.whale.booking.domain.masterdata.model.MasterDataOption;
import com.openhis.whale.booking.domain.masterdata.service.BookingPatientQueryService;
import com.openhis.whale.booking.domain.masterdata.service.BookingScheduleMasterDataQueryService;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleSubstituteInfoModel;
import com.openhis.whale.booking.domain.schedule.service.ScheduleQueryService;
import com.openhis.whale.booking.domain.appointment.entity.Appointment;
import com.openhis.whale.booking.domain.appointment.queries.ActiveOutpatientBookingLookupQuery;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentBookingDetailModel;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentQueuePageModel;
import com.openhis.whale.booking.domain.appointment.service.AppointmentDomainService;
import com.openhis.whale.booking.domain.appointment.service.AppointmentQueryService;
import com.openhis.whale.booking.domain.queue.entity.QueueDisplayConfig;
import com.openhis.whale.booking.domain.queue.service.QueueDisplayQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 候诊叫号应用服务（Q 线）。
 *
 * <p>职责边界：报到、叫号、过号、队列查询；写侧唯一入口。</p>
 * <p>接入：POST /api/app/queueBoard/{methodName}</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class QueueBoardAppService {

    /** 预约写侧：报到、叫号、过号、队列顺序调整。 */
    private final AppointmentDomainService appointmentDomainService;
    /** 预约读侧：队列分页、快照、预约解析。 */
    private final AppointmentQueryService appointmentQueryService;
    /** 叫号屏配置读侧。 */
    private final QueueDisplayQueryService queueDisplayQueryService;
    /** 患者主数据（health 经 Reader/Writer/Client）。 */
    private final BookingPatientQueryService bookingPatientQueryService;
    /** 排班主数据：医师姓名等。 */
    private final BookingScheduleMasterDataQueryService bookingScheduleMasterDataQueryService;
    /** 日程读侧：替诊信息。 */
    private final ScheduleQueryService scheduleQueryService;
    /** 替诊展示文案构建。 */
    private final OutpatientScheduleMapper outpatientScheduleMapper;
    /** Request/Command/DTO 映射。 */
    private final QueueBoardMapper queueBoardMapper;
    /** 写路径操作人。 */
    private final BookingOperatorResolver bookingOperatorResolver;
    /** 审计日志。 */
    private final BookingAuditSupport bookingAuditSupport;

    /**
     * 报到入队（5.1）：预约患者更新 displayOrder，现场患者新建 CHECK_IN_ONLY。
     */
    public CheckInResponse checkIn(@Valid CheckInRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：按预约单号解析 appointmentId（现场报到则 null）。
        Long appointmentId = this.resolveAppointmentIdForCheckIn(request);
        Appointment appointment;
        if (appointmentId != null) {
            request.setAppointmentId(appointmentId);
            // Cmd：预约报到入队。
            appointment = appointmentDomainService.checkInOutpatientBooking(
                queueBoardMapper.toCheckInOutpatientBookingCommand(request, operatorId));
        } else {
            this.assertWalkInCheckInRequest(request);
            // Cmd：现场报到新建 CHECK_IN_ONLY 并入队。
            appointment = appointmentDomainService.checkInToQueue(
                queueBoardMapper.toCheckInToQueueCommand(request, operatorId));
        }
        // Asm：映射报到响应。
        return queueBoardMapper.toCheckInResponse(appointment);
    }

    /**
     * 叫号看诊（5.2）。
     */
    public CallToSeeResponse callToSee(@Valid CallToSeeRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：叫号前加载队列快照与患者姓名。
        AppointmentQueuePageModel snapshot = appointmentQueryService.loadActiveQueueItemById(request.getAppointmentId());
        String patientName = this.resolvePatientName(snapshot.getPatientId());
        LocalDateTime calledAt = LocalDateTime.now();
        // Cmd：更新叫号状态。
        appointmentDomainService.callToSee(
            queueBoardMapper.toCallToSeeCommand(request.getAppointmentId(), request.getPractitionerId(), operatorId));
        bookingAuditSupport.record("Appointment", request.getAppointmentId(), "CALL_TO_SEE", null);
        // Asm：映射叫号响应。
        return queueBoardMapper.toCallToSeeResponse(snapshot, patientName, request.getPractitionerId(), calledAt);
    }

    /**
     * 过号处理（5.3）。
     */
    public void noShow(@Valid NoShowRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：标记过号。
        appointmentDomainService.noShow(
            queueBoardMapper.toNoShowCommand(request.getAppointmentId(), request.getReasonCode(), operatorId));
    }

    /**
     * 候诊队列分页（5.6）。
     */
    public PageQueueResponse pageQueue(@Valid PageQueueRequest request) {
        // Pre：分页加载活跃候诊队列并批量解析患者与替诊信息。
        Page<AppointmentQueuePageModel> page = appointmentQueryService.findQueueByOrganizationAndLocation(
            request.getOrganizationId(),
            request.getDestinationLocationId(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        Map<Long, String> patientNames = bookingPatientQueryService.findPatientNamesByIds(this.collectPatientIds(page.getContent()));
        Map<Long, ScheduleSubstituteInfoModel> substituteBySlot = this.resolveSubstituteBySlot(page.getContent());
        Map<Long, String> practitionerNames = this.loadPractitionerNameMap(request.getOrganizationId());
        List<QueueListItemDto> rows = new ArrayList<>();
        for (AppointmentQueuePageModel model : page.getContent()) {
            QueueListItemDto row = queueBoardMapper.toQueueListItemDto(model, patientNames);
            this.applySubstituteDisplay(row, model.getSlotId(), substituteBySlot, practitionerNames);
            rows.add(row);
        }
        // Asm：映射队列列表项并包装分页结果。
        return BookingPagedResultBuilder.build(new PageQueueResponse(), rows, page);
    }

    /**
     * 优先就诊（5.4）：将指定预约提升至队列前列。
     *
     * @param request 预约主键与目标诊室
     */
    public void priorityTreatment(@Valid PriorityTreatmentRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：调整队列优先级。
        appointmentDomainService.priorityTreatment(
            queueBoardMapper.toPriorityTreatmentCommand(request, operatorId));
    }

    /**
     * 过号顺延（5.5）：将当前患者向后移动一位。
     *
     * @param request 预约主键与移动方向
     */
    public void changeOver(@Valid ChangeOverRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：队列顺序后移。
        appointmentDomainService.changeOver(queueBoardMapper.toChangeOverCommand(request, operatorId));
    }

    /**
     * 拖拽调序（5.5）：将预约移动到目标序号。
     *
     * @param request 预约主键与目标索引
     */
    public void dragChangeOrder(@Valid DragChangeOrderRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：按目标索引重排 displayOrder。
        appointmentDomainService.dragChangeOrder(queueBoardMapper.toDragChangeOrderCommand(request, operatorId));
    }

    /**
     * 叫号屏快照（5.7）：当前叫号 + 等候列表 + 屏显配置。
     *
     * @param request 机构与诊室
     * @return 叫号屏展示快照
     */
    public QueueDisplaySnapshotResponse getQueueDisplaySnapshot(@Valid GetQueueDisplaySnapshotRequest request) {
        // Pre：加载屏显配置与活跃队列快照。
        QueueDisplayConfig config = queueDisplayQueryService
            .findByOrganizationAndLocation(request.getOrganizationId(), request.getDestinationLocationId())
            .orElse(null);
        List<AppointmentQueuePageModel> snapshot = appointmentQueryService.listActiveQueueSnapshot(
            request.getOrganizationId(), request.getDestinationLocationId());
        Map<Long, String> patientNames = bookingPatientQueryService.findPatientNamesByIds(this.collectPatientIds(snapshot));
        Map<Long, ScheduleSubstituteInfoModel> substituteBySlot = this.resolveSubstituteBySlot(snapshot);
        Map<Long, String> practitionerNames = this.loadPractitionerNameMap(request.getOrganizationId());
        // Asm：映射快照并附加替诊展示。
        QueueDisplaySnapshotResponse response = queueBoardMapper.toQueueDisplaySnapshotResponse(config, snapshot, patientNames);
        this.enrichSnapshotSubstitute(snapshot, response, substituteBySlot, practitionerNames);
        return response;
    }

    /**
     * 已完成队列分页（5.8）。
     */
    public PageCompletedQueueResponse pageCompletedQueue(@Valid PageCompletedQueueRequest request) {
        // Pre：分页加载已完成队列并批量解析患者与替诊信息。
        Page<AppointmentQueuePageModel> page = appointmentQueryService.pageCompletedQueue(
            request.getOrganizationId(),
            request.getDestinationLocationId(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        Map<Long, String> patientNames = bookingPatientQueryService.findPatientNamesByIds(this.collectPatientIds(page.getContent()));
        Map<Long, ScheduleSubstituteInfoModel> substituteBySlot = this.resolveSubstituteBySlot(page.getContent());
        Map<Long, String> practitionerNames = this.loadPractitionerNameMap(request.getOrganizationId());
        List<QueueListItemDto> rows = new ArrayList<>();
        for (AppointmentQueuePageModel model : page.getContent()) {
            QueueListItemDto row = queueBoardMapper.toQueueListItemDto(model, patientNames);
            this.applySubstituteDisplay(row, model.getSlotId(), substituteBySlot, practitionerNames);
            rows.add(row);
        }
        // Asm：映射已完成队列列表并包装分页结果。
        return BookingPagedResultBuilder.build(new PageCompletedQueueResponse(), rows, page);
    }

    private Map<Long, ScheduleSubstituteInfoModel> resolveSubstituteBySlot(List<AppointmentQueuePageModel> rows) {
        Set<Long> slotIds = new LinkedHashSet<>();
        for (AppointmentQueuePageModel row : rows) {
            if (row != null && row.getSlotId() != null) {
                slotIds.add(row.getSlotId());
            }
        }
        return scheduleQueryService.findSubstituteInfoBySlotIds(slotIds);
    }

    private Map<Long, String> loadPractitionerNameMap(Long organizationId) {
        Map<Long, String> nameMap = new HashMap<>();
        if (organizationId == null) {
            return nameMap;
        }
        List<MasterDataOption> practitioners = bookingScheduleMasterDataQueryService
            .loadOutpatientScheduleFormOptions(organizationId).getPractitioners();
        if (practitioners == null) {
            return nameMap;
        }
        for (MasterDataOption option : practitioners) {
            if (option == null || option.getValue() == null) {
                continue;
            }
            try {
                nameMap.put(Long.parseLong(option.getValue()), option.getLabel());
            } catch (NumberFormatException ignored) {
                // skip invalid option
            }
        }
        return nameMap;
    }

    private void applySubstituteDisplay(
        QueueListItemDto row,
        Long slotId,
        Map<Long, ScheduleSubstituteInfoModel> substituteBySlot,
        Map<Long, String> practitionerNames) {
        if (row == null || slotId == null || substituteBySlot == null) {
            return;
        }
        ScheduleSubstituteInfoModel info = substituteBySlot.get(slotId);
        if (info == null || info.getSubstitutePractitionerId() == null) {
            return;
        }
        row.setSubstituteDisplay(outpatientScheduleMapper.buildSubstituteDisplay(
            info.getSubstitutePractitionerId(), info.getOriginalPractitionerId(), practitionerNames));
    }

    private void enrichSnapshotSubstitute(
        List<AppointmentQueuePageModel> snapshot,
        QueueDisplaySnapshotResponse response,
        Map<Long, ScheduleSubstituteInfoModel> substituteBySlot,
        Map<Long, String> practitionerNames) {
        if (response == null || snapshot == null) {
            return;
        }
        Map<Long, Long> slotByAppointmentId = new HashMap<>();
        for (AppointmentQueuePageModel model : snapshot) {
            if (model != null && model.getId() != null) {
                slotByAppointmentId.put(model.getId(), model.getSlotId());
            }
        }
        if (response.getWaitingList() != null) {
            for (QueueListItemDto row : response.getWaitingList()) {
                this.applySubstituteDisplay(
                    row, slotByAppointmentId.get(row.getAppointmentId()), substituteBySlot, practitionerNames);
            }
        }
    }

    private void assertWalkInCheckInRequest(CheckInRequest request) {
        if (StringUtils.hasText(request.getAppointmentBusNo())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "预约单号无效或预约不可报到");
        }
        if (request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "现场报到 encounterId 不能为空");
        }
        if (request.getPatientId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "现场报到 patientId 不能为空");
        }
        if (request.getDestinationLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "现场报到 destinationLocationId 不能为空");
        }
    }

    private String resolvePatientName(Long patientId) {
        if (patientId == null) {
            return null;
        }
        Map<Long, String> names = bookingPatientQueryService.findPatientNamesByIds(List.of(patientId));
        return names.get(patientId);
    }

    private List<Long> collectPatientIds(List<AppointmentQueuePageModel> rows) {
        Set<Long> ids = new LinkedHashSet<>();
        for (AppointmentQueuePageModel row : rows) {
            if (row.getPatientId() != null) {
                ids.add(row.getPatientId());
            }
        }
        return new ArrayList<>(ids);
    }

    private Long resolveAppointmentIdForCheckIn(CheckInRequest request) {
        if (request.getAppointmentId() != null) {
            return request.getAppointmentId();
        }
        if (!StringUtils.hasText(request.getAppointmentBusNo())) {
            return null;
        }
        ActiveOutpatientBookingLookupQuery lookup = new ActiveOutpatientBookingLookupQuery();
        lookup.setAppointmentBusNo(request.getAppointmentBusNo().trim());
        lookup.setRegistrationDate(LocalDate.now());
        return appointmentQueryService.findActiveOutpatientBookingForRegister(lookup)
            .map(AppointmentBookingDetailModel::getId)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "预约单号无效或预约不可报到"));
    }
}
