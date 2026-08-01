/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationrequest.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 长期医嘱停嘱命令。
 *
 * <p>{@link #operatorId} 同时作为停嘱人写入 {@code stopper_id}。</p>
 */
@Getter
@Setter
public class StopLongTermMedicationOrderCommand {
    /** 操作人标识（写入停嘱人 {@code stopper_id}）。 */
    private Long operatorId;
    /** 药品申请标识。 */
    private Long id;
    /** 停嘱时间（空则领域取当前时刻）。 */
    private LocalDateTime stopTime;
}
