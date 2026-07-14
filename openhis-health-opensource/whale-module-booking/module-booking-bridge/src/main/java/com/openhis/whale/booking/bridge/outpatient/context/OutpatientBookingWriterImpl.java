/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.bridge.outpatient.context;

import com.openhis.whale.booking.bridge.slot.context.BookingSlotReaderImpl;
import com.openhis.whale.booking.domain.appointment.command.CancelAppointmentCommand;
import com.openhis.whale.booking.domain.appointment.command.CheckInOutpatientBookingCommand;
import com.openhis.whale.booking.domain.appointment.command.CreateAppointmentCommand;
import com.openhis.whale.booking.domain.appointment.command.LinkEncounterCommand;
import com.openhis.whale.booking.domain.appointment.command.RegistrationCancelReason;
import com.openhis.whale.booking.domain.appointment.command.ReleaseEncounterLinkCommand;
import com.openhis.whale.booking.domain.appointment.command.WalkInSlotAllocateCommand;
import com.openhis.whale.booking.domain.appointment.entity.Appointment;
import com.openhis.whale.booking.domain.appointment.repository.AppointmentRepository;
import com.openhis.whale.booking.domain.appointment.service.AppointmentDomainService;
import com.openhis.whale.booking.domain.policy.entity.RegistrationPolicy;
import com.openhis.whale.booking.domain.policy.service.PolicyQueryService;
import com.openhis.whale.booking.domain.share.AppointmentKindConstants;
import com.openhis.whale.booking.domain.share.RegistrationCancelPolicyConstants;
import com.openhis.whale.booking.domain.slot.command.AllocateBookingCommand;
import com.openhis.whale.booking.domain.slot.command.ReleaseBookingCommand;
import com.openhis.whale.booking.domain.slot.entity.Slot;
import com.openhis.whale.booking.domain.slot.repository.SlotRepository;
import com.openhis.whale.booking.domain.slot.service.SlotDomainService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * DEP-REG 挂号衔接写侧（orchestrator 专用）。
 *
 * <p>职责边界：health 挂号流程回调 booking 写预约关联、现场取号、退号处置；可依赖 booking-domain + health-domain。</p>
 * <p>禁止依赖 booking-application；编排尽量薄，复杂不变量留在领域层。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientBookingWriterImpl {

    /** 预约写侧：关联就诊、取消、签到等。 */
    private final AppointmentDomainService appointmentDomainService;
    /** 预约仓储：读当前预约事实（adapter 层少量直读，避免重复领域方法）。 */
    private final AppointmentRepository appointmentRepository;
    /** 号源查找：现场取号时解析首个可用 slot。 */
    private final BookingSlotReaderImpl bookingSlotReader;
    /** 号源写侧：占用/释放容量。 */
    private final SlotDomainService slotDomainService;
    /** 号源读：现场占号后回填预约起止时刻。 */
    private final SlotRepository slotRepository;
    /** 挂号衔接策略查询。 */
    private final PolicyQueryService policyQueryService;

    /**
     * 挂号成功后把 encounter 写回预约（IF-REG 衔接）。
     *
     * @param appointmentId 预约主键
     * @param encounterId 就诊主键
     */
    public void linkEncounter(Long appointmentId, Long encounterId) {
        // 缺主键时静默跳过，health 侧可能重复回调。
        if (appointmentId == null || encounterId == null) {
            return;
        }
        LinkEncounterCommand command = new LinkEncounterCommand();
        LinkEncounterCommand.LinkEncounterItemCommand item = new LinkEncounterCommand.LinkEncounterItemCommand();
        item.setAppointmentId(appointmentId);
        item.setEncounterId(encounterId);
        command.setItems(List.of(item));
        appointmentDomainService.linkEncounter(command);
    }

    /**
     * 现场取号：占用 slot，并落门诊 Appointment（绑定 encounter），供退号释号。
     *
     * @param command 取号上下文（须含 encounterId）
     * @return 占用的 slotId；无可用号源或缺少就诊锚点时 {@code null}
     */
    public Long allocateWalkInSlot(WalkInSlotAllocateCommand command) {
        if (command == null || command.getOrganizationId() == null || command.getHealthcareServiceId() == null) {
            return null;
        }
        // Gate：退号按 encounter 找预约；无就诊则只占号无法回补余量，拒绝。
        if (command.getEncounterId() == null || command.getPatientId() == null) {
            return null;
        }
        LocalDate registrationDate;
        if (command.getRegistrationDate() != null) {
            registrationDate = command.getRegistrationDate();
        } else {
            registrationDate = LocalDate.now();
        }
        Long slotId = command.getSlotId();
        if (slotId == null) {
            // Pre：在当日可用号源中解析首个 free 且有余量的 slot。
            slotId = bookingSlotReader.findFirstAvailableSlotId(
                command.getOrganizationId(),
                registrationDate,
                command.getPractitionerId(),
                command.getHealthcareServiceId());
        }
        if (slotId == null) {
            return null;
        }
        AllocateBookingCommand allocateCommand = new AllocateBookingCommand();
        AllocateBookingCommand.AllocateBookingItemCommand allocateItem =
            new AllocateBookingCommand.AllocateBookingItemCommand();
        allocateItem.setSlotId(slotId);
        allocateCommand.setOperatorId(command.getOperatorId());
        allocateCommand.setItems(List.of(allocateItem));
        // Cmd：先占容量，再写预约行；预约失败则回滚占号，避免余量泄漏。
        slotDomainService.allocateBooking(allocateCommand);
        try {
            this.createWalkInAppointment(command, slotId, registrationDate);
        } catch (RuntimeException ex) {
            this.releaseSlotQuietly(slotId, command.getOperatorId());
            throw ex;
        }
        return slotId;
    }

    /**
     * 挂号后按院点策略决定是否自动签到（autoCheckInOnRegister）。
     *
     * @param appointmentId 预约主键
     * @param encounterId 就诊主键（本方法未直接使用，保留 contracts Writer 契约）
     */
    public void checkInAfterRegister(Long appointmentId, Long encounterId) {
        if (appointmentId == null || encounterId == null) {
            return;
        }
        Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null || Boolean.TRUE.equals(appointment.getIsDeleted())) {
            return;
        }
        RegistrationPolicy policy = policyQueryService.findRegistrationPolicyByOrganization(appointment.getOrganizationId())
            .orElse(null);
        // 未配置或未开启自动签到则直接返回。
        if (policy == null || !Boolean.TRUE.equals(policy.getAutoCheckInOnRegister())) {
            return;
        }
        CheckInOutpatientBookingCommand command = new CheckInOutpatientBookingCommand();
        if (appointment.getOrigin() != null) {
            command.setOperatorId(appointment.getOrigin().getOriginOperatorId());
        } else {
            command.setOperatorId(null);
        }
        CheckInOutpatientBookingCommand.CheckInOutpatientBookingItemCommand item =
            new CheckInOutpatientBookingCommand.CheckInOutpatientBookingItemCommand();
        item.setAppointmentId(appointmentId);
        item.setOrganizationId(appointment.getOrganizationId());
        item.setDestinationLocationId(appointment.getDestinationLocationId());
        command.setItems(List.of(item));
        appointmentDomainService.checkInOutpatientBooking(command);
    }

    /**
     * 退号回调：按 onCancelPolicy 释放号源占用并处理预约关联。
     *
     * <p>{@link RegistrationCancelPolicyConstants#RELEASE_SLOT_ONLY}（默认）与
     * {@link RegistrationCancelPolicyConstants#CANCEL_APPOINTMENT} 均释放 bookedCount；
     * {@link RegistrationCancelPolicyConstants#KEEP_SLOT} 仅解绑就诊。</p>
     *
     * @param encounterId 就诊主键
     * @param reason 退号原因与操作人
     */
    public void onRegistrationCancelled(Long encounterId, RegistrationCancelReason reason) {
        if (encounterId == null) {
            return;
        }
        Appointment appointment = appointmentRepository.findByEncounterId(encounterId).orElse(null);
        if (appointment == null || Boolean.TRUE.equals(appointment.getIsDeleted())) {
            return;
        }
        // 非门诊预约类型不参与挂号衔接处置。
        if (!AppointmentKindConstants.OUTPATIENT_BOOKING.equals(appointment.getAppointmentKind())) {
            return;
        }
        String cancelPolicy = this.resolveOnCancelPolicy(appointment.getOrganizationId(), reason);
        Long operatorId = null;
        if (reason != null) {
            operatorId = reason.getOperatorId();
        }
        String cancelReasonText = "退号取消预约";
        if (reason != null && reason.getCancelReason() != null && !reason.getCancelReason().isBlank()) {
            cancelReasonText = reason.getCancelReason();
        }

        // KEEP_SLOT：挂号退号后仍保留号源占用（极少用）；只解绑就诊。
        if (RegistrationCancelPolicyConstants.KEEP_SLOT.equalsIgnoreCase(cancelPolicy)) {
            this.releaseEncounterLinkOnly(encounterId);
            return;
        }

        // RELEASE_SLOT_ONLY / CANCEL_APPOINTMENT：回补余量。
        if (appointment.getSlotId() != null) {
            if (operatorId == null && appointment.getOrigin() != null) {
                operatorId = appointment.getOrigin().getOriginOperatorId();
            }
            // 缺操作人无法审计释号；抛错让事务回滚，避免余量静默泄漏。
            if (operatorId == null) {
                throw new BusinessException("BKG-REG-500", "退号释号缺少操作人，无法回补号源余量");
            }
            this.releaseSlotQuietly(appointment.getSlotId(), operatorId);
        }

        // CANCEL_APPOINTMENT：预约行一并终态；其余默认仅解绑 encounter。
        if (RegistrationCancelPolicyConstants.CANCEL_APPOINTMENT.equalsIgnoreCase(cancelPolicy)) {
            CancelAppointmentCommand cancelCommand = new CancelAppointmentCommand();
            cancelCommand.setOperatorId(operatorId);
            CancelAppointmentCommand.CancelAppointmentItemCommand cancelItem =
                new CancelAppointmentCommand.CancelAppointmentItemCommand();
            cancelItem.setAppointmentId(appointment.getId());
            cancelItem.setReason(cancelReasonText);
            cancelItem.setCancellationReasonCode("REGISTRATION_CANCELLED");
            cancelCommand.setItems(List.of(cancelItem));
            appointmentDomainService.cancelAppointment(cancelCommand);
            return;
        }

        this.releaseEncounterLinkOnly(encounterId);
    }

    /**
     * 现场挂号：占号后创建绑定就诊的门诊预约行。
     *
     * @param command 占号命令
     * @param slotId 已占用号源
     * @param registrationDate 挂号自然日
     */
    private void createWalkInAppointment(
        WalkInSlotAllocateCommand command,
        Long slotId,
        LocalDate registrationDate) {
        CreateAppointmentCommand createCommand = new CreateAppointmentCommand();
        createCommand.setOperatorId(command.getOperatorId());
        CreateAppointmentCommand.CreateAppointmentItemCommand item =
            new CreateAppointmentCommand.CreateAppointmentItemCommand();
        item.setAppointmentKind(AppointmentKindConstants.OUTPATIENT_BOOKING);
        item.setOrganizationId(command.getOrganizationId());
        item.setPatientId(command.getPatientId());
        item.setEncounterId(command.getEncounterId());
        item.setSlotId(slotId);
        item.setHealthcareServiceId(command.getHealthcareServiceId());
        item.setBookingDate(registrationDate);
        Slot slot = slotRepository.findById(slotId).orElse(null);
        if (slot != null) {
            item.setStartInstant(slot.getStartTime());
            item.setEndInstant(slot.getEndTime());
        }
        CreateAppointmentCommand.AppointmentOriginCommand origin =
            new CreateAppointmentCommand.AppointmentOriginCommand();
        origin.setOriginChannel("COUNTER");
        origin.setOriginOperatorId(command.getOperatorId());
        item.setOrigin(origin);
        createCommand.setItems(List.of(item));
        appointmentDomainService.create(createCommand);
    }

    /**
     * 解析退号处置策略：请求覆盖 &gt; 院点策略 &gt; 默认 RELEASE_SLOT_ONLY。
     *
     * @param organizationId 科室主键
     * @param reason 退号入参
     * @return 策略码
     */
    private String resolveOnCancelPolicy(Long organizationId, RegistrationCancelReason reason) {
        if (reason != null && reason.getPolicyCode() != null && !reason.getPolicyCode().isBlank()) {
            return reason.getPolicyCode().trim();
        }
        RegistrationPolicy policy = policyQueryService.findRegistrationPolicyByOrganization(organizationId)
            .orElse(null);
        if (policy != null && policy.getOnCancelPolicy() != null && !policy.getOnCancelPolicy().isBlank()) {
            return policy.getOnCancelPolicy().trim();
        }
        return RegistrationCancelPolicyConstants.RELEASE_SLOT_ONLY;
    }

    /**
     * 仅解除就诊与预约关联，不改 bookedCount。
     *
     * @param encounterId 就诊主键
     */
    private void releaseEncounterLinkOnly(Long encounterId) {
        ReleaseEncounterLinkCommand command = new ReleaseEncounterLinkCommand();
        ReleaseEncounterLinkCommand.ReleaseEncounterLinkItemCommand item =
            new ReleaseEncounterLinkCommand.ReleaseEncounterLinkItemCommand();
        item.setEncounterId(encounterId);
        command.setItems(List.of(item));
        appointmentDomainService.releaseEncounterLink(command);
    }

    /**
     * 释放号源占用（回补余量）。
     *
     * @param slotId 号源主键
     * @param operatorId 操作人
     */
    private void releaseSlotQuietly(Long slotId, Long operatorId) {
        ReleaseBookingCommand releaseCommand = new ReleaseBookingCommand();
        releaseCommand.setOperatorId(operatorId);
        ReleaseBookingCommand.ReleaseBookingItemCommand releaseItem =
            new ReleaseBookingCommand.ReleaseBookingItemCommand();
        releaseItem.setSlotId(slotId);
        releaseCommand.setItems(List.of(releaseItem));
        slotDomainService.releaseBooking(releaseCommand);
    }
}
