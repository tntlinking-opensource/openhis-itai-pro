/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.bridge.outpatient.workflow;

import com.openhis.whale.booking.bridge.outpatient.context.OutpatientBookingReaderImpl;
import com.openhis.whale.booking.domain.appointment.command.BookingRegistrationValidateCommand;
import com.openhis.whale.booking.domain.appointment.result.BookableServiceOption;
import com.openhis.whale.booking.domain.slot.result.BookableSlotHint;
import com.openhis.whale.booking.domain.appointment.result.OutpatientBookingSnapshot;
import com.openhis.whale.booking.domain.policy.entity.RegistrationPolicy;
import com.openhis.whale.booking.domain.policy.service.PolicyQueryService;
import com.openhis.whale.booking.domain.appointment.queries.OutpatientBookingLookupQuery;
import com.openhis.whale.booking.domain.share.RegistrationWalkInSlotModeConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 门诊预约查询编排（IF-REG 读侧链）。
 *
 * <p>职责：有效预约检索、可约号源类型/时段列表、挂号前校验；读侧委托
 * {@link OutpatientBookingReaderImpl}，策略判断经 {@link PolicyQueryService}。</p>
 */
@Component
@RequiredArgsConstructor
public class BookingOutpatientQueryOrchestrator {

    private final OutpatientBookingReaderImpl outpatientBookingReader;
    private final PolicyQueryService policyQueryService;

    /** 按患者/机构/日期检索有效门诊预约。 */
    public Optional<OutpatientBookingSnapshot> findActiveOutpatientBooking(OutpatientBookingLookupQuery query) {
        return outpatientBookingReader.findActiveOutpatientBooking(query);
    }

    /** 按预约主键加载门诊预约快照。 */
    public Optional<OutpatientBookingSnapshot> loadOutpatientBookingById(Long appointmentId) {
        return outpatientBookingReader.loadOutpatientBookingById(appointmentId);
    }

    /** 列出医师当日可约号源类型（IF-REG-01 收窄）。 */
    public List<BookableServiceOption> listBookableServiceTypes(
            Long organizationId, Long practitionerId, LocalDate date) {
        return outpatientBookingReader.listBookableServiceTypes(organizationId, practitionerId, date);
    }

    /** 列出指定号源类型当日可约时段（IF-REG-14）。 */
    public List<BookableSlotHint> listBookableSlots(
            Long organizationId, Long practitionerId, Long healthcareServiceId, LocalDate date) {
        return outpatientBookingReader.listBookableSlots(organizationId, practitionerId, healthcareServiceId, date);
    }

    /** 挂号前校验预约状态与号源一致性。 */
    public void validateForRegistration(BookingRegistrationValidateCommand command) {
        outpatientBookingReader.validateForRegistration(command);
    }

    /**
     * 挂号号源下拉是否与已放号 Schedule 求交。
     *
     * <p>产品口径：手动挂号与用排班挂号解绑，恒为 false（保留方法签名兼容既有调用方）。</p>
     *
     * @param organizationId 科室/机构主键（未使用）
     * @return 恒 false
     */
    public boolean shouldIntersectRegisterServicesWithReleasedSlots(Long organizationId) {
        return false;
    }

    /**
     * 院点策略：无 {@code slotId} 的现场挂号是否自动占当日 Slot。
     *
     * <p>挂号 IF-REG-06 已改为仅显式 {@code slotId} 占号，本方法不再驱动预结算；保留供策略读侧展示。</p>
     *
     * @param organizationId 科室/机构主键
     * @return 策略为 {@code WALK_IN_WITH_SLOT} 时 true
     */
    public boolean shouldAllocateWalkInSlotForWalkInRegistration(Long organizationId) {
        return RegistrationWalkInSlotModeConstants.WALK_IN_WITH_SLOT
            .equalsIgnoreCase(this.resolveWalkInSlotMode(organizationId));
    }

    /**
     * 解析院点现场占号模式；无配置或空白时默认 {@link RegistrationWalkInSlotModeConstants#WALK_IN_WITH_SLOT}。
     *
     * @param organizationId 科室/机构主键
     * @return 占号模式码值
     */
    public String resolveWalkInSlotMode(Long organizationId) {
        if (organizationId == null) {
            return RegistrationWalkInSlotModeConstants.WALK_IN_WITH_SLOT;
        }
        RegistrationPolicy policy = policyQueryService.findRegistrationPolicyByOrganization(organizationId).orElse(null);
        if (policy == null || policy.getWalkInSlotMode() == null || policy.getWalkInSlotMode().isBlank()) {
            return RegistrationWalkInSlotModeConstants.WALK_IN_WITH_SLOT;
        }
        return policy.getWalkInSlotMode().trim();
    }
}
