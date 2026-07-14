/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 撤回已发放耗材命令。
 */
@Getter
@Setter
public class RecallDispensedDeviceDispenseCommand {

    /** 主键。 */
    private Long id;

    /** 原因。 */
    private String reason;

    /** 操作人主键。 */
    private Long operatorId;
}
