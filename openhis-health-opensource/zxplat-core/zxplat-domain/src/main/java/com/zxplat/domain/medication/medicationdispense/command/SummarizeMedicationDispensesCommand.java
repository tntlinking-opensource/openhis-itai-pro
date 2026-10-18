/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 汇总发放命令。
 */
@Getter
@Setter
public class SummarizeMedicationDispensesCommand {
    /** 操作人标识。 */
    private Long operatorId;
    /** 发放位置标识。 */
    private Long locationId;
    /** 待汇总发放单标识集合。 */
    private List<Long> dispenseIds;
    /** 预分配汇总单号（MSO；与 Supply {@code bus_no} 同源，应用层 IF-IP-SUM-04 必填）。 */
    private String summaryNo;
}
