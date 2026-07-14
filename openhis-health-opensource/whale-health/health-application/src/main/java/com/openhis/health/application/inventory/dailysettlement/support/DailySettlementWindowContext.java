/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.dailysettlement.support;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 库房日结业务时间窗上下文（BR-INV-SET-004）。
 */
@Getter
@Setter
public class DailySettlementWindowContext {

    /** 期初备份单号。 */
    private String startBackupBusNo;

    /** 期末备份单号；空表示实时期末。 */
    private String endBackupBusNo;

    /** 期初备份自然日 D₀（零点备份日；快照内容为 (D₀−1) 日终库存）。 */
    private LocalDate openingBackupDate;

    /** 期末自然日（备份日或查询日）。 */
    private LocalDate endingDate;

    /** 供应/发药审批窗下界（含）。 */
    private LocalDateTime approvalWindowStartInclusive;

    /** 供应/发药审批窗上界（含）。 */
    private LocalDateTime approvalWindowEndInclusive;

    /** 期末是否取实时库存。 */
    private boolean endUsesRealtime;
}
