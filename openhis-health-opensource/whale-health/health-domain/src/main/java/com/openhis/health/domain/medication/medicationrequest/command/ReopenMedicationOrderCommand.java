/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationrequest.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 重开药品医嘱命令。
 */
@Getter
@Setter
/**
 * ReopenMedicationOrderCommand。
 */
public class ReopenMedicationOrderCommand {
    /** 操作人标识。 */
    /**
     * operatorId。
     */
    private Long operatorId;
    /** 原始药品申请标识。 */
    /**
     * originRequestId。
     */
    private Long originRequestId;
}
