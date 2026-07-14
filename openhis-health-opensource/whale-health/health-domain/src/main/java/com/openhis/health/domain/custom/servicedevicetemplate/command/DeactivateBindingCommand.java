/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.servicedevicetemplate.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 停用绑定命令。
 */
@Getter
@Setter
public class DeactivateBindingCommand {
    /**
     * 绑定记录标识。
     */
    private Long bindingId;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
