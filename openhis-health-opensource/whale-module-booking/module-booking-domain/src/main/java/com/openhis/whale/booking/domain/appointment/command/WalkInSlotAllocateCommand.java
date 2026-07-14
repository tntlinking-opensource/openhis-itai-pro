/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.appointment.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 现场挂号占当日 Slot（模式 C）。
 *
 * <p>占号同时写入门诊 Appointment，并把 {@code encounterId} 绑上，退号才能按就诊找回并释放余量。</p>
 */
@Getter
@Setter
public class WalkInSlotAllocateCommand {

    /** 挂号科室主键。 */
    private Long organizationId;

    /** 接诊医师主键（可选）。 */
    private Long practitionerId;

    /** 号源类型主键。 */
    private Long healthcareServiceId;

    /** 挂号自然日。 */
    private LocalDate registrationDate;

    /** 患者主键。 */
    private Long patientId;

    /** 操作人（占号审计）。 */
    private Long operatorId;

    /** 指定占用的号源主键；为空时取当日首个可用号源。 */
    private Long slotId;

    /**
     * 已开立的就诊主键。
     *
     * <p>须在 encounter 落库后传入，以便写 {@code wkf_appointment_queue.encounter_id} 供退号释号。</p>
     */
    private Long encounterId;
}
