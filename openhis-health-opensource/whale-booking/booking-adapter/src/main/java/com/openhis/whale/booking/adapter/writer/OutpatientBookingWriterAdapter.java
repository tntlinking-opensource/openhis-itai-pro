package com.openhis.whale.booking.adapter.writer;

import com.openhis.whale.booking.adapter.support.BookingSlotLookupSupport;
import com.openhis.whale.booking.contracts.command.RegistrationCancelReason;
import com.openhis.whale.booking.contracts.command.WalkInSlotAllocateCommand;
import com.openhis.whale.booking.contracts.writer.OutpatientBookingWriter;
import com.openhis.whale.booking.domain.appointment.command.CancelAppointmentCommand;
import com.openhis.whale.booking.domain.appointment.command.CheckInOutpatientBookingCommand;
import com.openhis.whale.booking.domain.appointment.command.LinkEncounterCommand;
import com.openhis.whale.booking.domain.appointment.command.ReleaseEncounterLinkCommand;
import com.openhis.whale.booking.domain.appointment.entity.Appointment;
import com.openhis.whale.booking.domain.appointment.repository.AppointmentRepository;
import com.openhis.whale.booking.domain.appointment.service.AppointmentDomainService;
import com.openhis.whale.booking.domain.policy.entity.RegistrationPolicy;
import com.openhis.whale.booking.domain.policy.service.PolicyQueryService;
import com.openhis.whale.booking.domain.share.AppointmentKindConstants;
import com.openhis.whale.booking.domain.slot.command.AllocateBookingCommand;
import com.openhis.whale.booking.domain.slot.command.ReleaseBookingCommand;
import com.openhis.whale.booking.domain.slot.service.SlotDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * {@link OutpatientBookingWriter} 默认实现（DEP-REG 挂号衔接）。
 *
 * <p>职责边界：health 挂号流程回调 booking 写预约关联、现场取号、退号处置；可依赖 booking-domain + health-domain。</p>
 * <p>禁止依赖 booking-application；编排尽量薄，复杂不变量留在领域层。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientBookingWriterAdapter implements OutpatientBookingWriter {

    /** 院点策略：退号时取消预约并释放号源（onCancelPolicy=CANCEL_APPOINTMENT）。 */
    private static final String CANCEL_APPOINTMENT_POLICY = "CANCEL_APPOINTMENT";

    /** 预约写侧：关联就诊、取消、签到等。 */
    private final AppointmentDomainService appointmentDomainService;
    /** 预约仓储：读当前预约事实（adapter 层少量直读，避免重复领域方法）。 */
    private final AppointmentRepository appointmentRepository;
    /** 号源查找：现场取号时解析首个可用 slot。 */
    private final BookingSlotLookupSupport bookingSlotLookupSupport;
    /** 号源写侧：占用/释放容量。 */
    private final SlotDomainService slotDomainService;
    /** 挂号衔接策略查询。 */
    private final PolicyQueryService policyQueryService;

    /**
     * 挂号成功后把 encounter 写回预约（IF-REG 衔接）。
     *
     * @param appointmentId 预约主键
     * @param encounterId 就诊主键
     */
    @Override
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
     * 现场取号：按机构/号源类型/医师解析首个可用 slot 并占用（walk-in）。
     *
     * @param command 取号上下文
     * @return 占用的 slotId；无可用号源时 {@code null}
     */
    @Override
    public Long allocateWalkInSlot(WalkInSlotAllocateCommand command) {
        if (command == null || command.getOrganizationId() == null || command.getHealthcareServiceId() == null) {
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
            slotId = bookingSlotLookupSupport.findFirstAvailableSlotId(
                command.getOrganizationId(),
                registrationDate,
                command.getPractitionerId(),
                command.getHealthcareServiceId());
        }
        if (slotId == null) {
            return null;
        }
        AllocateBookingCommand allocateCommand = new AllocateBookingCommand();
        AllocateBookingCommand.AllocateBookingItemCommand item = new AllocateBookingCommand.AllocateBookingItemCommand();
        item.setSlotId(slotId);
        allocateCommand.setOperatorId(command.getOperatorId());
        allocateCommand.setItems(List.of(item));
        slotDomainService.allocateBooking(allocateCommand);
        return slotId;
    }

    /**
     * 挂号后按院点策略决定是否自动签到（autoCheckInOnRegister）。
     *
     * @param appointmentId 预约主键
     * @param encounterId 就诊主键（本方法未直接使用，保留 Port 契约）
     */
    @Override
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
     * 退号回调：按 onCancelPolicy 取消预约并可选释放号源，或仅解除 encounter 关联。
     *
     * @param encounterId 就诊主键
     * @param reason 退号原因
     */
    @Override
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
        RegistrationPolicy policy = policyQueryService.findRegistrationPolicyByOrganization(appointment.getOrganizationId())
            .orElse(null);
        String cancelPolicy;
        if (policy != null) {
            cancelPolicy = policy.getOnCancelPolicy();
        } else {
            cancelPolicy = "RELEASE_SLOT_ONLY";
        }

        // 策略为取消预约：同时释放 slot 占用。
        if (CANCEL_APPOINTMENT_POLICY.equals(cancelPolicy)) {
            CancelAppointmentCommand cancelCommand = new CancelAppointmentCommand();
            cancelCommand.setOperatorId(null);
            CancelAppointmentCommand.CancelAppointmentItemCommand cancelItem =
                new CancelAppointmentCommand.CancelAppointmentItemCommand();
            cancelItem.setAppointmentId(appointment.getId());
            String cancelReason;
            if (reason != null && reason.getCancelReason() != null) {
                cancelReason = reason.getCancelReason();
            } else {
                cancelReason = "退号取消预约";
            }
            cancelItem.setReason(cancelReason);
            cancelItem.setCancellationReasonCode("REGISTRATION_CANCELLED");
            cancelCommand.setItems(List.of(cancelItem));
            appointmentDomainService.cancelAppointment(cancelCommand);

            if (appointment.getSlotId() != null) {
                ReleaseBookingCommand releaseCommand = new ReleaseBookingCommand();
                releaseCommand.setOperatorId(null);
                ReleaseBookingCommand.ReleaseBookingItemCommand releaseItem =
                    new ReleaseBookingCommand.ReleaseBookingItemCommand();
                releaseItem.setSlotId(appointment.getSlotId());
                releaseCommand.setItems(List.of(releaseItem));
                slotDomainService.releaseBooking(releaseCommand);
            }
            return;
        }

        // 默认策略：仅解除 encounter 关联，保留预约与号源占用事实。
        ReleaseEncounterLinkCommand command = new ReleaseEncounterLinkCommand();
        ReleaseEncounterLinkCommand.ReleaseEncounterLinkItemCommand item =
            new ReleaseEncounterLinkCommand.ReleaseEncounterLinkItemCommand();
        item.setEncounterId(encounterId);
        command.setItems(List.of(item));
        appointmentDomainService.releaseEncounterLink(command);
    }
}
