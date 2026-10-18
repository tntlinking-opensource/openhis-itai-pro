/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 修正耗材发放展示时刻命令（发放时间轴 = 客户可见 recorded 轴）。
 *
 * <p>已发（有 {@code dispense_time}）改 {@code dispense_time}；仅拣配（有 {@code prepare_time}、无发放）改
 * {@code prepare_time}。不改 {@code planned_dispense_time}。供护士站划价改时（IF-IP-PRC-07）。</p>
 */
@Getter
@Setter
public class AmendDeviceDispenseDisplayTimeCommand {

    /** 发放单主键。 */
    private Long dispenseId;

    /** 新的客户可见发放/拣配时刻。 */
    private LocalDateTime displayTime;

    /** 操作人（adm_practitioner.id；可选）。 */
    private Long operatorId;
}
