/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 取消发放汇总命令。
 */
@Getter
@Setter
/**
 * CancelMedicationDispenseSummaryCommand。
 */
public class CancelMedicationDispenseSummaryCommand {
    /** 操作人标识。 */
    /**
     * operatorId。
     */
    private Long operatorId;
    /** 汇总单号。 */
    /**
     * summaryNo。
     */
    private String summaryNo;
}
