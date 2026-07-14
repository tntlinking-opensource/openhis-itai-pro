/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 完成汇总发药命令。
 */
@Getter
@Setter
public class CompleteSummaryDispenseCommand {
    /** 操作人标识。 */
    private Long operatorId;
    /** 汇总单号。 */
    private String summaryNo;
    /** 发药人标识。 */
    private Long practitionerId;
    /** 调配药师标识（汇总核发一步写入发放行，无独立保存配药接口）。 */
    private Long preparerId;
}
