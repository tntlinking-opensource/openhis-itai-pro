package com.openhis.whale.booking.application.support;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.application.outpatient.mapper.OutpatientScheduleMapper;
import com.openhis.whale.booking.application.outpatient.request.PreviewStopClinicImpactRequest;
import com.openhis.whale.booking.application.outpatient.request.StopClinicTransferItemRequest;
import com.openhis.whale.booking.application.outpatient.request.TransferAppointmentsOnStopClinicRequest;
import com.openhis.whale.booking.application.outpatient.response.PreviewStopClinicImpactResponse;
import com.openhis.whale.booking.application.outpatient.response.StopClinicImpactItemDto;
import com.openhis.whale.booking.application.outpatient.response.StopClinicTransferResultItemDto;
import com.openhis.whale.booking.application.outpatient.response.TransferAppointmentsOnStopClinicResponse;
import com.openhis.whale.booking.application.register.mapper.BookingRegisterMapper;
import com.openhis.whale.booking.application.register.request.CancelBookingRequest;
import com.openhis.whale.booking.application.register.request.RescheduleBookingRequest;
import com.openhis.whale.booking.contracts.reader.HealthPatientReader;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentBookingDetailModel;
import com.openhis.whale.booking.domain.appointment.service.AppointmentDomainService;
import com.openhis.whale.booking.domain.appointment.service.AppointmentQueryService;
import com.openhis.whale.booking.domain.policy.entity.ScheduleChangePolicy;
import com.openhis.whale.booking.domain.policy.service.PolicyQueryService;
import com.openhis.whale.booking.application.slot.mapper.SlotManageMapper;
import com.openhis.whale.booking.domain.schedule.command.DeactivateScheduleCommand;
import com.openhis.whale.booking.domain.slot.command.BlockSlotCommand;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleCalendarItemModel;
import com.openhis.whale.booking.domain.schedule.service.ScheduleDomainService;
import com.openhis.whale.booking.domain.schedule.service.ScheduleQueryService;
import com.openhis.whale.booking.domain.stopclinic.service.StopClinicSlotFreezeDomainService;
import com.openhis.whale.booking.domain.slot.queries.FindSlotsByScheduleAndTimeRangeQuery;
import com.openhis.whale.booking.domain.slot.queries.model.SlotDetailModel;
import com.openhis.whale.booking.domain.slot.service.SlotDomainService;
import com.openhis.whale.booking.domain.slot.service.SlotQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.openhis.whale.booking.domain.share.StopClinicConstants;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 停诊影响预览与预约转交编排（07 §4，2.18～2.19）。
 * <p>写路径须由调用方 {@code *AppService}（如 {@link com.openhis.whale.booking.application.outpatient.OutpatientScheduleAppService}）开启 UoW；本类禁止 {@code @Transactional}。</p>
 */
@Service
@RequiredArgsConstructor
public class StopClinicImpactService {

    private final ScheduleQueryService scheduleQueryService;
    private final SlotQueryService slotQueryService;
    private final AppointmentQueryService appointmentQueryService;
    private final PolicyQueryService policyQueryService;
    private final StopClinicTransferMatcher stopClinicTransferMatcher;
    private final HealthPatientReader healthPatientReader;
    private final BookingRegisterMapper bookingRegisterMapper;
    private final SlotDomainService slotDomainService;
    private final AppointmentDomainService appointmentDomainService;
    private final ScheduleDomainService scheduleDomainService;
    private final OutpatientScheduleMapper outpatientScheduleMapper;
    private final BookingOperatorResolver bookingOperatorResolver;
    private final BookingAuditSupport bookingAuditSupport;
    private final SlotManageMapper slotManageMapper;
    private final StopClinicSlotFreezeDomainService stopClinicSlotFreezeDomainService;

    /**
     * 停诊影响预览：扫描号源、预约并生成转交映射建议。
     *
     * @param request 预览请求
     * @return 影响摘要与映射表
     */
    public PreviewStopClinicImpactResponse previewImpact(PreviewStopClinicImpactRequest request) {
        ScheduleCalendarItemModel schedule = scheduleQueryService.loadCalendarItemById(request.getScheduleId());
        if (!request.getOrganizationId().equals(schedule.getOrganizationId())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "日程与科室不匹配");
        }
        ScheduleChangePolicy policy = policyQueryService.resolveScheduleChangePolicy(request.getOrganizationId());
        List<SlotDetailModel> affectedSlots = this.resolveAffectedSlots(schedule, request.getSlotIds());
        List<Long> slotIds = new ArrayList<>();
        Map<Long, SlotDetailModel> slotById = new HashMap<>();
        for (SlotDetailModel slot : affectedSlots) {
            slotIds.add(slot.getId());
            slotById.put(slot.getId(), slot);
        }
        List<AppointmentBookingDetailModel> appointments =
            appointmentQueryService.listActiveOutpatientBookingsBySlotIds(slotIds);
        Map<Long, String> patientNames = healthPatientReader.findPatientNamesByIds(this.collectPatientIds(appointments));

        PreviewStopClinicImpactResponse response = new PreviewStopClinicImpactResponse();
        response.setScheduleId(schedule.getId());
        response.setScheduleCount(1);
        response.setSlotCount(slotIds.size());
        response.setAppointmentCount(appointments.size());
        response.setBlockDeactivateIfUnhandled(policy.getBlockDeactivateIfUnhandled());
        response.setDefaultDispositionMode(policy.getOnPractitionerStopMode());
        response.setTransferMatchRule(policy.getTransferMatchRule());

        int registeredCount = 0;
        int checkedInCount = 0;
        int channelCount = 0;
        boolean previewTransfer = StopClinicConstants.DISPOSITION_TRANSFER.equals(request.getDispositionMode());
        if (!previewTransfer && request.getDispositionMode() == null) {
            previewTransfer = StopClinicConstants.DISPOSITION_TRANSFER.equals(policy.getOnPractitionerStopMode());
        }

        for (AppointmentBookingDetailModel appointment : appointments) {
            StopClinicImpactItemDto item = new StopClinicImpactItemDto();
            item.setAppointmentId(appointment.getId());
            item.setBusNo(appointment.getBusNo());
            item.setPatientId(appointment.getPatientId());
            item.setPatientName(patientNames.get(appointment.getPatientId()));
            item.setSourceSlotId(appointment.getSlotId());
            SlotDetailModel sourceSlot = slotById.get(appointment.getSlotId());
            if (sourceSlot != null) {
                item.setSourceStartTime(sourceSlot.getStartTime());
                item.setSourceEndTime(sourceSlot.getEndTime());
            }
            String dispositionStatus = this.resolveDispositionStatus(appointment);
            item.setDispositionStatus(dispositionStatus);
            if (StopClinicConstants.ROW_SKIP_REGISTERED.equals(dispositionStatus)) {
                registeredCount++;
                item.setDispositionMessage("已挂号，须走 health 退号");
            } else if (StopClinicConstants.ROW_SKIP_CHECKED_IN.equals(dispositionStatus)) {
                checkedInCount++;
                item.setDispositionMessage("已报到或已叫号，仅人工处置");
            } else {
                if (previewTransfer && request.getTargetPractitionerId() != null && sourceSlot != null) {
                    Long healthcareServiceId = schedule.getHealthcareServiceId();
                    boolean requireSameService = Boolean.TRUE.equals(policy.getTransferRequireSameService());
                    java.util.Optional<Long> targetSlotId = stopClinicTransferMatcher.matchTargetSlotId(
                        request.getOrganizationId(),
                        healthcareServiceId,
                        request.getTargetPractitionerId(),
                        sourceSlot,
                        schedule.getId(),
                        policy.getTransferMatchRule(),
                        requireSameService);
                    if (targetSlotId.isPresent()) {
                        SlotDetailModel targetSlot = slotQueryService.loadSlotById(targetSlotId.get());
                        item.setTargetSlotId(targetSlot.getId());
                        item.setTargetStartTime(targetSlot.getStartTime());
                        item.setTargetEndTime(targetSlot.getEndTime());
                        item.setDispositionStatus(StopClinicConstants.ROW_CAN_TRANSFER);
                        item.setDispositionMessage("可转交");
                    } else {
                        item.setDispositionStatus(StopClinicConstants.ROW_MANUAL_REQUIRED);
                        item.setDispositionMessage("未找到可用目标号源");
                    }
                } else {
                    item.setDispositionMessage("待选择处置方式或目标医师");
                }
            }
            if (StringUtils.isNotBlank(appointment.getOriginChannel())) {
                channelCount++;
            }
            response.getItems().add(item);
        }
        response.setRegisteredCount(registeredCount);
        response.setCheckedInCount(checkedInCount);
        response.setChannelCount(channelCount);

        if (Boolean.TRUE.equals(policy.getFreezeSourceSlotOnPreview()) && !slotIds.isEmpty()) {
            Long operatorId = bookingOperatorResolver.requireOperatorId();
            int ttlMinutes;
            if (policy.getFreezeTtlMinutes() != null) {
                ttlMinutes = policy.getFreezeTtlMinutes();
            } else {
                ttlMinutes = 15;
            }
            LocalDateTime expiresAt = stopClinicSlotFreezeDomainService.applyPreviewFreeze(
                request.getOrganizationId(),
                schedule.getId(),
                slotIds,
                ttlMinutes,
                operatorId);
            response.setSourceSlotsFrozen(Boolean.TRUE);
            response.setFrozenSlotCount(slotIds.size());
            response.setFreezeExpiresAt(expiresAt);
        } else {
            response.setSourceSlotsFrozen(Boolean.FALSE);
            response.setFrozenSlotCount(0);
        }
        return response;
    }

    /**
     * 取消停诊预览时释放源号源冻结。
     */
    public void releasePreviewFreeze(
        Long organizationId,
        Long scheduleId,
        List<Long> slotIds,
        Long operatorId) {
        ScheduleCalendarItemModel schedule = scheduleQueryService.loadCalendarItemById(scheduleId);
        if (!organizationId.equals(schedule.getOrganizationId())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "日程与科室不匹配");
        }
        stopClinicSlotFreezeDomainService.releasePreviewFreeze(scheduleId, slotIds, operatorId);
    }

    /**
     * 批量转交/取消预约并停诊。
     *
     * @param request 执行请求
     * @return 执行结果
     */
    public TransferAppointmentsOnStopClinicResponse transferAndStop(TransferAppointmentsOnStopClinicRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        ScheduleCalendarItemModel schedule = scheduleQueryService.loadCalendarItemById(request.getScheduleId());
        if (!request.getOrganizationId().equals(schedule.getOrganizationId())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "日程与科室不匹配");
        }
        ScheduleChangePolicy policy = policyQueryService.resolveScheduleChangePolicy(request.getOrganizationId());
        List<SlotDetailModel> affectedSlots = this.resolveAffectedSlots(schedule, request.getSlotIds());
        List<Long> slotIds = new ArrayList<>();
        Map<Long, SlotDetailModel> slotById = new HashMap<>();
        for (SlotDetailModel slot : affectedSlots) {
            slotIds.add(slot.getId());
            slotById.put(slot.getId(), slot);
        }
        List<AppointmentBookingDetailModel> appointments =
            appointmentQueryService.listActiveOutpatientBookingsBySlotIds(slotIds);
        Map<Long, Long> explicitTargets = this.toExplicitTargetMap(request.getTransferItems());

        TransferAppointmentsOnStopClinicResponse response = new TransferAppointmentsOnStopClinicResponse();
        response.setSuccessCount(0);
        response.setFailedCount(0);
        response.setScheduleDeactivated(Boolean.FALSE);

        int pendingManual = 0;
        for (AppointmentBookingDetailModel appointment : appointments) {
            String disposition = this.resolveDispositionStatus(appointment);
            if (StopClinicConstants.ROW_SKIP_REGISTERED.equals(disposition)
                || StopClinicConstants.ROW_SKIP_CHECKED_IN.equals(disposition)) {
                pendingManual++;
                continue;
            }
            if (StopClinicConstants.DISPOSITION_MANUAL.equals(request.getDispositionMode())) {
                pendingManual++;
                continue;
            }
            if (StopClinicConstants.DISPOSITION_CANCEL.equals(request.getDispositionMode())) {
                this.executeCancel(appointment, request.getReason(), operatorId, response);
                continue;
            }
            if (StopClinicConstants.DISPOSITION_TRANSFER.equals(request.getDispositionMode())) {
                this.executeTransfer(
                    appointment,
                    request,
                    schedule,
                    slotById,
                    explicitTargets,
                    policy,
                    operatorId,
                    response);
            }
        }

        if (Boolean.TRUE.equals(policy.getBlockDeactivateIfUnhandled()) && pendingManual > 0) {
            if (!Boolean.TRUE.equals(request.getDeferManualHandling())) {
                throw new BusinessException(
                    ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT,
                    "存在需人工处置的预约，请完成转交/取消或勾选稍后人工");
            }
        }

        List<Long> requestedSlotIds = this.normalizeSlotIds(request.getSlotIds());
        if (!requestedSlotIds.isEmpty()) {
            String reason;
            if (request.getReason() != null) {
                reason = request.getReason();
            } else {
                reason = "停诊";
            }
            for (Long slotId : requestedSlotIds) {
                BlockSlotCommand blockCommand = slotManageMapper.toBlockSlotCommand(slotId, reason, operatorId);
                slotDomainService.blockSlot(blockCommand);
            }
            response.setBlockedSlotCount(requestedSlotIds.size());
            response.setScheduleDeactivated(Boolean.FALSE);
            bookingAuditSupport.record(
                "Schedule",
                request.getScheduleId(),
                "BLOCK_SLOTS_ON_STOP_CLINIC",
                request.getDispositionMode());
        } else {
            DeactivateScheduleCommand command = outpatientScheduleMapper.toDeactivateScheduleCommand(
                this.buildDeactivateRequest(request.getScheduleId(), request.getReason()), operatorId);
            scheduleDomainService.deactivateSchedule(command);
            response.setScheduleDeactivated(Boolean.TRUE);
            bookingAuditSupport.record(
                "Schedule",
                request.getScheduleId(),
                "STOP_CLINIC_WITH_DISPOSITION",
                request.getDispositionMode());
        }
        List<Long> releaseSlotIds;
        if (requestedSlotIds.isEmpty()) {
            releaseSlotIds = slotIds;
        } else {
            releaseSlotIds = requestedSlotIds;
        }
        stopClinicSlotFreezeDomainService.releasePreviewFreeze(
            request.getScheduleId(),
            releaseSlotIds,
            operatorId);
        return response;
    }

    private void executeTransfer(
        AppointmentBookingDetailModel appointment,
        TransferAppointmentsOnStopClinicRequest request,
        ScheduleCalendarItemModel schedule,
        Map<Long, SlotDetailModel> slotById,
        Map<Long, Long> explicitTargets,
        ScheduleChangePolicy policy,
        Long operatorId,
        TransferAppointmentsOnStopClinicResponse response) {
        Long targetSlotId = explicitTargets.get(appointment.getId());
        if (targetSlotId == null && request.getTargetPractitionerId() != null) {
            SlotDetailModel sourceSlot = slotById.get(appointment.getSlotId());
            if (sourceSlot != null) {
                java.util.Optional<Long> matched = stopClinicTransferMatcher.matchTargetSlotId(
                    request.getOrganizationId(),
                    schedule.getHealthcareServiceId(),
                    request.getTargetPractitionerId(),
                    sourceSlot,
                    schedule.getId(),
                    policy.getTransferMatchRule(),
                    Boolean.TRUE.equals(policy.getTransferRequireSameService()));
                if (matched.isPresent()) {
                    targetSlotId = matched.get();
                }
            }
        }
        if (targetSlotId == null) {
            this.addFailed(response, appointment.getId(), null, "未匹配到目标号源");
            return;
        }
        try {
            if (appointment.getSlotId() != null) {
                slotDomainService.releaseBooking(
                    bookingRegisterMapper.toReleaseBookingCommand(appointment.getSlotId(), operatorId));
            }
            slotDomainService.allocateBooking(
                bookingRegisterMapper.toAllocateBookingCommand(targetSlotId, operatorId));
            SlotDetailModel newSlot = slotQueryService.loadSlotById(targetSlotId);
            RescheduleBookingRequest rescheduleRequest = new RescheduleBookingRequest();
            rescheduleRequest.setAppointmentId(appointment.getId());
            rescheduleRequest.setNewSlotId(targetSlotId);
            appointmentDomainService.rescheduleOutpatientBooking(
                bookingRegisterMapper.toRescheduleOutpatientBookingCommand(rescheduleRequest, newSlot, operatorId));
            this.addSuccess(response, appointment.getId(), targetSlotId, "转交成功");
            bookingAuditSupport.record("Appointment", appointment.getId(), "TRANSFER_ON_STOP_CLINIC", targetSlotId.toString());
        } catch (BusinessException ex) {
            this.addFailed(response, appointment.getId(), targetSlotId, ex.getMessage());
        }
    }

    private void executeCancel(
        AppointmentBookingDetailModel appointment,
        String reason,
        Long operatorId,
        TransferAppointmentsOnStopClinicResponse response) {
        try {
            if (appointment.getSlotId() == null) {
                this.addFailed(response, appointment.getId(), null, "预约未关联号源");
                return;
            }
            CancelBookingRequest cancelRequest = new CancelBookingRequest();
            cancelRequest.setAppointmentId(appointment.getId());
            if (reason != null) {
                cancelRequest.setReason(reason);
            } else {
                cancelRequest.setReason("停诊取消");
            }
            appointmentDomainService.cancelAppointment(
                bookingRegisterMapper.toCancelAppointmentCommand(cancelRequest, operatorId));
            slotDomainService.releaseBooking(
                bookingRegisterMapper.toReleaseBookingCommand(appointment.getSlotId(), operatorId));
            this.addSuccess(response, appointment.getId(), null, "已取消");
            bookingAuditSupport.record("Appointment", appointment.getId(), "CANCEL_ON_STOP_CLINIC", cancelRequest.getReason());
        } catch (BusinessException ex) {
            this.addFailed(response, appointment.getId(), null, ex.getMessage());
        }
    }

    private List<Long> normalizeSlotIds(List<Long> slotIds) {
        List<Long> result = new ArrayList<>();
        if (slotIds == null) {
            return result;
        }
        Set<Long> seen = new HashSet<>();
        for (Long slotId : slotIds) {
            if (slotId != null && seen.add(slotId)) {
                result.add(slotId);
            }
        }
        return result;
    }

    private com.openhis.whale.booking.application.outpatient.request.DeactivateScheduleRequest buildDeactivateRequest(
        Long scheduleId, String reason) {
        com.openhis.whale.booking.application.outpatient.request.DeactivateScheduleRequest deactivateRequest =
            new com.openhis.whale.booking.application.outpatient.request.DeactivateScheduleRequest();
        deactivateRequest.setScheduleId(scheduleId);
        deactivateRequest.setReason(reason);
        return deactivateRequest;
    }

    private List<SlotDetailModel> resolveAffectedSlots(ScheduleCalendarItemModel schedule, List<Long> slotIds) {
        if (slotIds != null && !slotIds.isEmpty()) {
            List<SlotDetailModel> slots = new ArrayList<>();
            for (Long slotId : slotIds) {
                SlotDetailModel slot = slotQueryService.loadSlotById(slotId);
                if (!schedule.getId().equals(slot.getScheduleId())) {
                    throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "号源不属于该日程");
                }
                slots.add(slot);
            }
            return slots;
        }
        if (schedule.getPlanningHorizonStart() == null || schedule.getPlanningHorizonEnd() == null) {
            return new ArrayList<>();
        }
        FindSlotsByScheduleAndTimeRangeQuery query = new FindSlotsByScheduleAndTimeRangeQuery();
        query.setScheduleId(schedule.getId());
        query.setStartTime(schedule.getPlanningHorizonStart());
        query.setEndTime(schedule.getPlanningHorizonEnd());
        return slotQueryService.findSlotsByScheduleAndTimeRange(query);
    }

    private String resolveDispositionStatus(AppointmentBookingDetailModel appointment) {
        if (appointment.getEncounterId() != null) {
            return StopClinicConstants.ROW_SKIP_REGISTERED;
        }
        if (appointment.getCalledAt() != null) {
            return StopClinicConstants.ROW_SKIP_CHECKED_IN;
        }
        if (appointment.getDisplayOrder() != null) {
            return StopClinicConstants.ROW_SKIP_CHECKED_IN;
        }
        return StopClinicConstants.ROW_CAN_TRANSFER;
    }

    private Map<Long, Long> toExplicitTargetMap(List<StopClinicTransferItemRequest> items) {
        Map<Long, Long> map = new HashMap<>();
        if (items == null) {
            return map;
        }
        for (StopClinicTransferItemRequest item : items) {
            if (item.getAppointmentId() != null && item.getTargetSlotId() != null) {
                map.put(item.getAppointmentId(), item.getTargetSlotId());
            }
        }
        return map;
    }

    private List<Long> collectPatientIds(List<AppointmentBookingDetailModel> appointments) {
        Set<Long> ids = new HashSet<>();
        for (AppointmentBookingDetailModel appointment : appointments) {
            if (appointment.getPatientId() != null) {
                ids.add(appointment.getPatientId());
            }
        }
        return new ArrayList<>(ids);
    }

    private void addSuccess(
        TransferAppointmentsOnStopClinicResponse response,
        Long appointmentId,
        Long targetSlotId,
        String message) {
        StopClinicTransferResultItemDto item = new StopClinicTransferResultItemDto();
        item.setAppointmentId(appointmentId);
        item.setTargetSlotId(targetSlotId);
        item.setMessage(message);
        response.getSuccessItems().add(item);
        response.setSuccessCount(response.getSuccessCount() + 1);
    }

    private void addFailed(
        TransferAppointmentsOnStopClinicResponse response,
        Long appointmentId,
        Long targetSlotId,
        String message) {
        StopClinicTransferResultItemDto item = new StopClinicTransferResultItemDto();
        item.setAppointmentId(appointmentId);
        item.setTargetSlotId(targetSlotId);
        item.setMessage(message);
        response.getFailedItems().add(item);
        response.setFailedCount(response.getFailedCount() + 1);
    }
}
