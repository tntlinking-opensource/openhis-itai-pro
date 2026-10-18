/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationrequest.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 退回药品医嘱命令。
 */
@Getter
@Setter
public class RejectMedicationOrderCommand {
    /** 操作人标识。 */
    private Long operatorId;
    /** 药品申请标识。 */
    private Long id;
    /** 校对人标识。 */
    private Long performerCheckId;
    /** 校对时间。 */
    private LocalDateTime checkTime;
    /** 退回原因。 */
    private String reason;
}
