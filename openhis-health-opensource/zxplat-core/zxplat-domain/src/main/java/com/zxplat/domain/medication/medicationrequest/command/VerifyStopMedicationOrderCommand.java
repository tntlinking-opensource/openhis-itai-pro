/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationrequest.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 药品长期医嘱停嘱校对命令：仅写停嘱校对人/时间，不改 {@code status_enum}（仍为 STOPPED）。
 */
@Getter
@Setter
public class VerifyStopMedicationOrderCommand {
    /** 操作人标识（事件追溯）。 */
    private Long operatorId;
    /** 药品申请主键。 */
    private Long id;
    /** 停嘱校对人（护士 Practitioner）。 */
    private Long stopPerformerCheckId;
    /** 停嘱校对时间；空则领域取当前时刻。 */
    private LocalDateTime stopCheckTime;
}
