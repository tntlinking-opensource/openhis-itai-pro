/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 临时医嘱执行后挂接已有发放单命令（IF-IP-EXE-02 临时药品路径）。
 */
@Getter
@Setter
public class LinkDispenseProcedureCommand {
    /** 发放单主键。 */
    private Long dispenseId;
    /** 执行记录主键。 */
    private Long procedureId;
    /** 计划发药时刻。 */
    private LocalDateTime plannedDispenseTime;
    /** 操作人主键。 */
    private Long operatorId;
}
