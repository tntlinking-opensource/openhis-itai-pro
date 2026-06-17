/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.whale.booking.domain.appointment.service;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.workflow.appointment.AppointmentCalledToSeeEvent;
import com.openhis.health.domain.share.events.workflow.appointment.AppointmentCreatedEvent;
import com.openhis.health.domain.share.events.workflow.appointment.AppointmentNoShowEvent;
import com.openhis.health.domain.share.events.workflow.appointment.PriorityTreatmentAppliedEvent;
import com.openhis.health.domain.share.events.workflow.appointment.QueueOrderChangedEvent;
import com.openhis.whale.booking.domain.appointment.command.CallToSeeCommand;
import com.openhis.whale.booking.domain.appointment.command.CancelAppointmentCommand;
import com.openhis.whale.booking.domain.appointment.command.CancelByEncounterCommand;
import com.openhis.whale.booking.domain.appointment.command.ChangeOverCommand;
import com.openhis.whale.booking.domain.appointment.command.CheckInOutpatientBookingCommand;
import com.openhis.whale.booking.domain.appointment.command.CheckInToQueueCommand;
import com.openhis.whale.booking.domain.appointment.command.CreateAppointmentCommand;
import com.openhis.whale.booking.domain.appointment.command.DragChangeOrderCommand;
import com.openhis.whale.booking.domain.appointment.command.LinkEncounterCommand;
import com.openhis.whale.booking.domain.appointment.command.NoShowCommand;
import com.openhis.whale.booking.domain.appointment.command.ReleaseEncounterLinkCommand;
import com.openhis.whale.booking.domain.appointment.command.PriorityTreatmentCommand;
import com.openhis.whale.booking.domain.appointment.command.RescheduleOutpatientBookingCommand;
import com.openhis.whale.booking.domain.appointment.entity.Appointment;
import com.openhis.whale.booking.domain.appointment.entity.AppointmentOrigin;
import com.openhis.whale.booking.domain.appointment.repository.AppointmentRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 预约与候诊写侧领域服务。
 *
 * <p>职责：创建候诊/预约项、候诊队列调序、叫号、过号、按就诊取消等。</p>
 *
 * <p>边界：分诊医学规则、号源算法、就诊与患者跨聚合校验由应用服务编排；本服务只写本聚合表。</p>
 */
@Service
@RequiredArgsConstructor
public class AppointmentDomainService {

    private static final String OUTPATIENT_BOOKING_KIND = "OUTPATIENT_BOOKING";
    private static final String CHECK_IN_ONLY_KIND = "CHECK_IN_ONLY";

    private final AppointmentRepository appointmentRepository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 预约/候诊 busNo：业务流水按日递增，前缀 {@link AggregateTypeConstants#APPOINTMENT}。 */
    private final BusinessSequenceService businessSequenceService;

    /**
     * 创建候诊/预约项。
     *
     * @param command 创建命令（支持 item/items）
     * @return 创建后的实体（批量时返回首条）
     */
    public Appointment create(CreateAppointmentCommand command) {
        List<CreateAppointmentCommand.CreateAppointmentItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            // DOC-CONFLICT: DM/SDD 语义为单次创建动作，但 BatchCommand 默认支持批量；本聚合按一次一条收敛，避免队列重排语义歧义。
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "创建候诊/预约项一次仅允许提交一条记录");
        }

        CreateAppointmentCommand.CreateAppointmentItemCommand item = items.get(0);

        Appointment appointment = new Appointment();
        appointment.setAppointmentKind(item.getAppointmentKind());
        appointment.setOrganizationId(item.getOrganizationId());
        appointment.setDestinationLocationId(item.getDestinationLocationId());
        appointment.setEncounterId(item.getEncounterId());
        appointment.setPatientId(item.getPatientId());
        if (item.getDisplayOrder() == null) {
            // 门诊预约创建时不入队，报到后再分配 displayOrder。
            if (!OUTPATIENT_BOOKING_KIND.equals(item.getAppointmentKind())) {
                appointment.setDisplayOrder(1);
            }
        } else {
            appointment.setDisplayOrder(item.getDisplayOrder());
        }
        appointment.setSlotId(item.getSlotId());
        appointment.setAppointmentTypeCode(item.getAppointmentTypeCode());
        appointment.setStartInstant(item.getStartInstant());
        appointment.setEndInstant(item.getEndInstant());
        appointment.setIsDeleted(Boolean.FALSE);
        appointment.setBusNo(this.generateBusNoIfNeeded(null));
        appointment.setOrigin(this.toOrigin(item.getOrigin()));

        // 分类约束校验。
        appointment.assertKindConstraints();

        // 先落库，再发布事件（DM-WKF-002 §5.1 全量载荷）。
        Appointment saved = appointmentRepository.save(appointment);
        String channel = null;
        if (saved.getOrigin() != null) {
            channel = saved.getOrigin().getOriginChannel();
        }
        domainEventPublisher.publish(new AppointmentCreatedEvent(
            saved.getId(), saved.getAppointmentKind(), saved.getBusNo(),
            saved.getPatientId(), saved.getEncounterId(), saved.getDisplayOrder(),
            channel, command.getOperatorId()));
        return saved;
    }

    /**
     * 渠道报到入队。
     *
     * @param command 报到入队命令（支持 item/items）
     * @return 创建后的实体（批量时返回首条）
     */
    public Appointment checkInToQueue(CheckInToQueueCommand command) {
        List<CheckInToQueueCommand.CheckInToQueueItemCommand> items = command.resolveItems();
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "报到入队一次仅允许提交一条记录");
        }

        CheckInToQueueCommand.CheckInToQueueItemCommand item = items.get(0);
        if (!CHECK_IN_ONLY_KIND.equals(item.getAppointmentKind())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "现场报到入队 appointmentKind 必须为 CHECK_IN_ONLY");
        }
        if (item.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "报到入队 organizationId 不能为空");
        }
        if (item.getDestinationLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "报到入队 destinationLocationId 不能为空");
        }
        this.assertNoActiveQueueForEncounter(item.getEncounterId());

        Appointment appointment = new Appointment();
        appointment.setAppointmentKind(item.getAppointmentKind());
        appointment.setOrganizationId(item.getOrganizationId());
        appointment.setDestinationLocationId(item.getDestinationLocationId());
        appointment.setEncounterId(item.getEncounterId());
        appointment.setPatientId(item.getPatientId());
        appointment.setDisplayOrder(this.resolveNextDisplayOrder(item.getOrganizationId(), item.getDestinationLocationId()));
        appointment.setIsDeleted(Boolean.FALSE);
        appointment.setBusNo(this.generateBusNoIfNeeded(null));
        appointment.setOrigin(this.toOrigin(item.getOrigin()));

        appointment.assertKindConstraints();

        Appointment saved = appointmentRepository.save(appointment);
        String checkInChannel = null;
        if (saved.getOrigin() != null) {
            checkInChannel = saved.getOrigin().getOriginChannel();
        }
        domainEventPublisher.publish(new AppointmentCreatedEvent(
            saved.getId(), saved.getAppointmentKind(), saved.getBusNo(),
            saved.getPatientId(), saved.getEncounterId(), saved.getDisplayOrder(),
            checkInChannel, command.getOperatorId()));
        return saved;
    }

    /**
     * 门诊预约报到入队（更新既有 OUTPATIENT_BOOKING 的 displayOrder）。
     *
     * @param command 预约报到命令
     * @return 更新后的实体
     */
    public Appointment checkInOutpatientBooking(CheckInOutpatientBookingCommand command) {
        List<CheckInOutpatientBookingCommand.CheckInOutpatientBookingItemCommand> items = command.resolveItems();
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "预约报到入队一次仅允许提交一条记录");
        }

        CheckInOutpatientBookingCommand.CheckInOutpatientBookingItemCommand item = items.get(0);
        if (item.getAppointmentId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "预约报到 appointmentId 不能为空");
        }
        if (item.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "预约报到 organizationId 不能为空");
        }

        Appointment appointment = this.requireAppointment(item.getAppointmentId());
        appointment.assertActive();
        if (!OUTPATIENT_BOOKING_KIND.equals(appointment.getAppointmentKind())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "仅门诊预约允许预约报到入队");
        }
        if (!item.getOrganizationId().equals(appointment.getOrganizationId())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "预约与机构不匹配");
        }
        if (appointment.getDisplayOrder() != null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "预约已报到入队");
        }

        Long destinationLocationId = item.getDestinationLocationId();
        if (destinationLocationId == null) {
            destinationLocationId = appointment.getDestinationLocationId();
        }
        if (destinationLocationId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "预约报到 destinationLocationId 不能为空");
        }
        appointment.setDestinationLocationId(destinationLocationId);
        appointment.setDisplayOrder(this.resolveNextDisplayOrder(item.getOrganizationId(), destinationLocationId));

        return appointmentRepository.save(appointment);
    }

    /**
     * 门诊预约回填就诊关联（IF-REG-07）。
     *
     * @param command 回填命令
     */
    public void linkEncounter(LinkEncounterCommand command) {
        List<LinkEncounterCommand.LinkEncounterItemCommand> items = command.resolveItems();
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "回填就诊关联一次仅允许提交一条记录");
        }
        LinkEncounterCommand.LinkEncounterItemCommand item = items.get(0);
        if (item.getAppointmentId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "回填就诊关联 appointmentId 不能为空");
        }
        if (item.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "回填就诊关联 encounterId 不能为空");
        }

        Appointment appointment = this.requireAppointment(item.getAppointmentId());
        appointment.assertActive();
        if (!OUTPATIENT_BOOKING_KIND.equals(appointment.getAppointmentKind())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "仅门诊预约允许回填就诊关联");
        }
        if (appointment.getEncounterId() != null) {
            if (appointment.getEncounterId().equals(item.getEncounterId())) {
                return;
            }
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "预约已关联其他就诊");
        }
        appointment.setEncounterId(item.getEncounterId());
        appointmentRepository.save(appointment);
    }

    /**
     * 退号后释放预约与就诊关联（IF-REG-10）。
     *
     * @param command 释放命令
     */
    public void releaseEncounterLink(ReleaseEncounterLinkCommand command) {
        List<ReleaseEncounterLinkCommand.ReleaseEncounterLinkItemCommand> items = command.resolveItems();
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "释放就诊关联一次仅允许提交一条记录");
        }
        ReleaseEncounterLinkCommand.ReleaseEncounterLinkItemCommand item = items.get(0);
        if (item.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "释放就诊关联 encounterId 不能为空");
        }

        Appointment appointment = appointmentRepository.findByEncounterId(item.getEncounterId()).orElse(null);
        if (appointment == null || Boolean.TRUE.equals(appointment.getIsDeleted())) {
            return;
        }
        if (!OUTPATIENT_BOOKING_KIND.equals(appointment.getAppointmentKind())) {
            return;
        }
        appointment.setEncounterId(null);
        appointmentRepository.save(appointment);
    }

    /**
     * 按预约主键取消有效门诊预约项。
     *
     * @param command 取消命令
     */
    public void cancelAppointment(CancelAppointmentCommand command) {
        List<CancelAppointmentCommand.CancelAppointmentItemCommand> items = command.resolveItems();
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "取消预约一次仅允许提交一条记录");
        }
        CancelAppointmentCommand.CancelAppointmentItemCommand item = items.get(0);
        if (item.getAppointmentId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "appointmentId 不能为空");
        }
        Appointment appointment = this.requireAppointment(item.getAppointmentId());
        appointment.assertActive();
        String reasonText = item.getReason();
        if (StringUtils.isBlank(reasonText)) {
            reasonText = "用户取消预约";
        }
        appointment.markTerminalByReason(reasonText, item.getCancellationReasonCode(), LocalDateTime.now());
        appointmentRepository.save(appointment);
        domainEventPublisher.publish(new AppointmentNoShowEvent(
            appointment.getId(), appointment.getCancellationAt(), item.getCancellationReasonCode()));
    }

    /**
     * 门诊预约改约：更新号源与时段快照（占释由应用层编排）。
     */
    public Appointment rescheduleOutpatientBooking(RescheduleOutpatientBookingCommand command) {
        if (command.getAppointmentId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "appointmentId 不能为空");
        }
        if (command.getNewSlotId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "newSlotId 不能为空");
        }
        Appointment appointment = this.requireAppointment(command.getAppointmentId());
        appointment.assertActive();
        if (!OUTPATIENT_BOOKING_KIND.equals(appointment.getAppointmentKind())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "仅门诊预约允许改约");
        }
        if (appointment.getEncounterId() != null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "已转挂号预约不可改约");
        }
        appointment.setSlotId(command.getNewSlotId());
        if (command.getNewStartInstant() != null) {
            appointment.setStartInstant(command.getNewStartInstant());
        }
        if (command.getNewEndInstant() != null) {
            appointment.setEndInstant(command.getNewEndInstant());
        }
        if (command.getDestinationLocationId() != null) {
            appointment.setDestinationLocationId(command.getDestinationLocationId());
        }
        return appointmentRepository.save(appointment);
    }

    /**
     * 按就诊取消当前有效项。
     *
     * @param command 取消命令（支持 item/items）
     */
    public void cancelByEncounterId(CancelByEncounterCommand command) {
        List<CancelByEncounterCommand.CancelByEncounterItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "按就诊取消一次仅允许提交一条记录");
        }

        CancelByEncounterCommand.CancelByEncounterItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "按就诊取消时 encounterId 不能为空");
        }

        Appointment appointment = appointmentRepository.findByEncounterId(item.getEncounterId()).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "未找到就诊对应的有效候诊/预约项"));

        // 仅取消当前有效项（BR-APT-003），取消进入终态并记录原因（BR-APT-004）。
        appointment.assertActive();
        appointment.markTerminalByReason(item.getReason(), item.getCancellationReasonCode(), LocalDateTime.now());

        appointmentRepository.save(appointment);
        domainEventPublisher.publish(new AppointmentNoShowEvent(
            appointment.getId(), appointment.getCancellationAt(), item.getCancellationReasonCode()));
    }

    /**
     * 优先就诊（置顶并重排）。
     *
     * @param command 优先就诊命令（支持 item/items）
     */
    public void priorityTreatment(PriorityTreatmentCommand command) {
        List<PriorityTreatmentCommand.PriorityTreatmentItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "优先就诊一次仅允许提交一条记录");
        }

        PriorityTreatmentCommand.PriorityTreatmentItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getAppointmentId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "优先就诊时 appointmentId 不能为空");
        }

        Appointment appointment = this.requireAppointment(item.getAppointmentId());
        appointment.assertActive();
        Long destinationLocationId;
        if (item.getDestinationLocationId() == null) {
            // 优先就诊未改目标科室时沿用预约原科室。
            destinationLocationId = appointment.getDestinationLocationId();
        } else {
            destinationLocationId = item.getDestinationLocationId();
        }
        // 业务分支处理。
        if (destinationLocationId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "优先就诊时 destinationLocationId 不能为空");
        }

        // BR-APT-002：同机构同诊室 displayOrder 连续；通过全量重排收口。
        List<Appointment> queue = appointmentRepository.findByOrganizationIdAndDestinationLocationId(
            appointment.getOrganizationId(), destinationLocationId);
        // 业务分支处理。
        if (queue.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "未找到同诊室的有效队列");
        }

        // 记录调序前序号，落库后作为事件载荷。
        Integer oldOrder = appointment.getDisplayOrder();
        this.moveToTopAndReorder(queue, appointment.getId());
        appointmentRepository.saveAll(queue);

        // 先落库再发布事件；优先后序号恒为 1（置顶）。
        domainEventPublisher.publish(new PriorityTreatmentAppliedEvent(appointment.getId(), oldOrder, appointment.getDisplayOrder()));
    }

    /**
     * 前后调位（与相邻项交换并重排）。
     *
     * @param command 调位命令（支持 item/items）
     */
    public void changeOver(ChangeOverCommand command) {
        List<ChangeOverCommand.ChangeOverItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "前后调位一次仅允许提交一条记录");
        }

        ChangeOverCommand.ChangeOverItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getAppointmentId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "前后调位时 appointmentId 不能为空");
        }
        // 业务分支处理。
        if (item.getMoveForward() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "前后调位时 moveForward 不能为空");
        }

        Appointment appointment = this.requireAppointment(item.getAppointmentId());
        appointment.assertActive();
        // 业务分支处理。
        if (appointment.getDestinationLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "前后调位时 destinationLocationId 不能为空");
        }

        List<Appointment> queue = appointmentRepository.findByOrganizationIdAndDestinationLocationId(
            appointment.getOrganizationId(), appointment.getDestinationLocationId());
        // 业务分支处理。
        if (queue.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "未找到同诊室的有效队列");
        }

        // 统一按 displayOrder 升序重排前先规范化 displayOrder，避免出现空值导致的越界判断错误。
        this.normalizeAndSort(queue);

        int index = this.indexOf(queue, appointment.getId());
        // 业务分支处理。
        if (index < 0) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "未找到目标候诊项");
        }

        int targetIndex;
        // 业务分支处理。
        if (item.getMoveForward()) {
            targetIndex = index - 1;
        } else {
            targetIndex = index + 1;
        }

        // BR-APT-006：调序不得越界。
        if (targetIndex < 0 || targetIndex >= queue.size()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "调位越界，禁止操作");
        }

        Integer oldOrder = appointment.getDisplayOrder();
        Collections.swap(queue, index, targetIndex);
        this.reorderDisplayOrder(queue);
        appointmentRepository.saveAll(queue);
        domainEventPublisher.publish(new QueueOrderChangedEvent(appointment.getId(), oldOrder, appointment.getDisplayOrder()));
    }

    /**
     * 拖拽调位（移动并重排）。
     *
     * @param command 拖拽调位命令（支持 item/items）
     */
    public void dragChangeOrder(DragChangeOrderCommand command) {
        List<DragChangeOrderCommand.DragChangeOrderItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "拖拽调位一次仅允许提交一条记录");
        }

        DragChangeOrderCommand.DragChangeOrderItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getAppointmentId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "拖拽调位时 appointmentId 不能为空");
        }
        // 业务分支处理。
        if (item.getTargetIndex() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "拖拽调位时 targetIndex 不能为空");
        }

        Appointment appointment = this.requireAppointment(item.getAppointmentId());
        appointment.assertActive();
        // 业务分支处理。
        if (appointment.getDestinationLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "拖拽调位时 destinationLocationId 不能为空");
        }

        List<Appointment> queue = appointmentRepository.findByOrganizationIdAndDestinationLocationId(
            appointment.getOrganizationId(), appointment.getDestinationLocationId());
        // 业务分支处理。
        if (queue.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "未找到同诊室的有效队列");
        }

        this.normalizeAndSort(queue);
        int index = this.indexOf(queue, appointment.getId());
        // 业务分支处理。
        if (index < 0) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "未找到目标候诊项");
        }

        int targetIndex = item.getTargetIndex();
        // 业务分支处理。
        if (targetIndex < 0 || targetIndex >= queue.size()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "拖拽目标索引越界");
        }

        Integer oldOrderDrag = appointment.getDisplayOrder();
        Appointment moved = queue.remove(index);
        queue.add(targetIndex, moved);
        this.reorderDisplayOrder(queue);
        appointmentRepository.saveAll(queue);
        domainEventPublisher.publish(new QueueOrderChangedEvent(appointment.getId(), oldOrderDrag, appointment.getDisplayOrder()));
    }

    /**
     * 叫号看诊。
     *
     * @param command 叫号命令（支持 item/items）
     */
    public void callToSee(CallToSeeCommand command) {
        List<CallToSeeCommand.CallToSeeItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "叫号看诊一次仅允许提交一条记录");
        }

        CallToSeeCommand.CallToSeeItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getAppointmentId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "叫号看诊时 appointmentId 不能为空");
        }
        // 业务分支处理。
        if (item.getPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "叫号看诊时 practitionerId 不能为空");
        }

        Appointment appointment = this.requireAppointment(item.getAppointmentId());
        appointment.assertActive();

        // 叫号进入终态并记录追溯字段（BR-APT-004）。
        appointment.setCalledAt(LocalDateTime.now());
        appointment.setLastPractitionerId(item.getPractitionerId());
        appointment.markTerminalByReason("医生叫号看诊", null, appointment.getCalledAt());

        appointmentRepository.save(appointment);
        domainEventPublisher.publish(new AppointmentCalledToSeeEvent(
            appointment.getId(), appointment.getCalledAt(), appointment.getLastPractitionerId()));
    }

    /**
     * 过号处理。
     *
     * @param command 过号命令（支持 item/items）
     */
    public void noShow(NoShowCommand command) {
        List<NoShowCommand.NoShowItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "过号处理一次仅允许提交一条记录");
        }

        NoShowCommand.NoShowItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getAppointmentId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "过号处理时 appointmentId 不能为空");
        }

        Appointment appointment = this.requireAppointment(item.getAppointmentId());
        appointment.assertActive();

        LocalDateTime noShowAt = LocalDateTime.now();
        appointment.markTerminalByReason("过号", item.getReasonCode(), noShowAt);
        appointmentRepository.save(appointment);
        domainEventPublisher.publish(new AppointmentNoShowEvent(appointment.getId(), noShowAt, item.getReasonCode()));
    }

    /**
     * 私有方法：requireAppointment。
     *
     * <p>按主键加载候诊/预约项，不存在则抛出业务异常。</p>
     *
     * @param appointmentId 候诊/预约项主键
     * @return 候诊/预约项实体
     * @throws BusinessException 候诊/预约项不存在时
     */
    private Appointment requireAppointment(Long appointmentId) {
        return appointmentRepository.findById(appointmentId).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "候诊/预约项不存在"));
    }

    /**
     * 私有方法：assertNoActiveQueueForEncounter。
     *
     * <p>BR-APT-001：候诊队列同一 encounterId 仅允许存在一条有效项的校验。</p>
     *
     * @param encounterId 就诊标识
     * @throws BusinessException encounterId 为空或已存在有效队列项时
     */
    private void assertNoActiveQueueForEncounter(Long encounterId) {
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "候诊队列形态 encounterId 不能为空");
        }
        // Pre：查询该就诊是否已存在有效队列项。
        Appointment existed = appointmentRepository.findByEncounterId(encounterId).orElse(null);
        if (existed != null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "同一就诊仅允许存在一条有效队列项");
        }
    }

    /**
     * 私有方法：moveToTopAndReorder。
     *
     * <p>将指定候诊项置顶并重排队列：先规范化排序，找到目标项移至首位，再重新分配序号。</p>
     * <ul>
     *   <li>Pre：规范化队列顺序</li>
     *   <li>Cmd：找到目标项并移至队列首位</li>
     *   <li>Asm：重新分配 displayOrder</li>
     * </ul>
     *
     * @param queue         队列列表
     * @param appointmentId 目标候诊项ID
     * @throws BusinessException 未找到目标候诊项时
     */
    private void moveToTopAndReorder(List<Appointment> queue, Long appointmentId) {
        // Pre：规范化队列排序，确保 displayOrder 有效。
        this.normalizeAndSort(queue);
        // Pre：查找目标项索引。
        int index = this.indexOf(queue, appointmentId);
        if (index < 0) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "未找到目标候诊项");
        }
        Appointment moved = queue.remove(index);
        queue.add(0, moved);
        // Asm：重新分配序号。
        this.reorderDisplayOrder(queue);
    }

    /**
     * 私有方法：normalizeAndSort。
     *
     * <p>规范化队列并排序：将 null displayOrder 设为 0，然后按 displayOrder 升序排序。</p>
     *
     * @param queue 队列列表
     */
    private void normalizeAndSort(List<Appointment> queue) {
        for (Appointment appointment : queue) {
            if (appointment.getDisplayOrder() == null) {
                appointment.setDisplayOrder(0);
            }
        }
        queue.sort((a, b) -> {
            Integer oa = a.getDisplayOrder();
            Integer ob = b.getDisplayOrder();
            // 业务分支处理：两者都为空视为相等。
            if (oa == null && ob == null) {
                return 0;
            }
            // 业务分支处理：a 为空则视为更小。
            if (oa == null) {
                return -1;
            }
            // 业务分支处理：b 为空则视为更大。
            if (ob == null) {
                return 1;
            }
            return oa.compareTo(ob);
        });
    }

    /**
     * 私有方法：indexOf。
     *
     * <p>在队列中查找指定候诊项的索引位置。</p>
     *
     * @param queue         队列列表
     * @param appointmentId 目标候诊项ID
     * @return 索引位置，未找到返回 -1
     */
    private int indexOf(List<Appointment> queue, Long appointmentId) {
        // Loop：遍历队列查找匹配项。
        for (int i = 0; i < queue.size(); i++) {
            Appointment appointment = queue.get(i);
            // 业务分支处理：ID 匹配则返回当前索引。
            if (appointmentId != null && appointmentId.equals(appointment.getId())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 私有方法：reorderDisplayOrder。
     *
     * <p>重新分配队列中各候诊项的 displayOrder，从 1 开始连续递增（BR-APT-002）。</p>
     *
     * @param queue 队列列表
     */
    private void reorderDisplayOrder(List<Appointment> queue) {
        // Loop：按队列顺序重新分配序号（从 1 开始）。
        for (int i = 0; i < queue.size(); i++) {
            Appointment appointment = queue.get(i);
            appointment.setDisplayOrder(i + 1);
        }
    }

    /**
     * 私有方法：toOrigin。
     *
     * <p>将创建命令中的来源信息转换为实体对象。</p>
     *
     * @param command 来源信息命令
     * @return 来源实体对象，命令为空时返回 null
     */
    private AppointmentOrigin toOrigin(CreateAppointmentCommand.AppointmentOriginCommand command) {
        // 业务分支处理：命令为空时返回 null。
        if (command == null) {
            return null;
        }
        // Asm：装配来源实体字段。
        AppointmentOrigin origin = new AppointmentOrigin();
        origin.setOriginChannel(command.getOriginChannel());
        origin.setOriginChannelSessionId(command.getOriginChannelSessionId());
        origin.setOriginSourceDeviceId(command.getOriginSourceDeviceId());
        origin.setOriginOperatorId(command.getOriginOperatorId());
        origin.setOriginClientAppVersion(command.getOriginClientAppVersion());
        return origin;
    }

    /**
     * 计算同诊室队列下一顺位（仅统计已分配 displayOrder 的有效项）。
     */
    private int resolveNextDisplayOrder(Long organizationId, Long destinationLocationId) {
        List<Appointment> queue = appointmentRepository.findByOrganizationIdAndDestinationLocationId(
            organizationId, destinationLocationId);
        int maxOrder = 0;
        for (Appointment row : queue) {
            if (row.getDisplayOrder() != null && row.getDisplayOrder() > maxOrder) {
                maxOrder = row.getDisplayOrder();
            }
        }
        return maxOrder + 1;
    }

    /**
     * 私有方法：generateBusNoIfNeeded。
     *
     * <p>按需生成业务编码：若外部已提供则使用，否则生成按日递增的序列号。</p>
     *
     * @param provided 外部提供的业务编码
     * @return 业务编码（传入值或生成值）
     */
    private String generateBusNoIfNeeded(String provided) {
        // 业务分支处理：外部提供有效编码时直接使用。
        if (StringUtils.isNotBlank(provided)) {
            return provided.trim();
        }
        return businessSequenceService.getSeqByDay(AggregateTypeConstants.APPOINTMENT, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
    }
}
