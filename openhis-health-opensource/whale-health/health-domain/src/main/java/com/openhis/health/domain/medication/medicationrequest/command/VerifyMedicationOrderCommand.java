/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationrequest.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 校对药品医嘱命令。
 */
@Getter
@Setter
public class VerifyMedicationOrderCommand {
    /** 操作人标识。 */
    private Long operatorId;
    /** 药品申请标识。 */
    private Long id;
    /** 校对人标识。 */
    private Long performerCheckId;
    /** 校对时间。 */
    private LocalDateTime checkTime;
}
