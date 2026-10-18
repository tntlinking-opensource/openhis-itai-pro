/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.procedure.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 修正执行记录展示时刻命令（仅改 {@code recorded_time}，不改 {@code occurrence_time}）。
 *
 * <p>对齐住院医嘱执行双轨：客户可见时间 = recorded；计划/计费锚点 = occurrence。
 * 供护士站划价改时（AS-IP-NUR-005 IF-IP-PRC-07）等已完成执行事实修正。</p>
 */
@Getter
@Setter
public class AmendProcedureRecordedTimeCommand {

    /** 执行记录主键。 */
    private Long procedureId;

    /** 新的客户可见执行/登记时刻（写入 recorded_time）。 */
    private LocalDateTime recordedTime;

    /** 操作人（adm_practitioner.id；可选，本命令不改 recorder_id）。 */
    private Long operatorId;
}
