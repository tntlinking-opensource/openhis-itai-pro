/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.servicedevicetemplate.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 启用绑定命令。
 */
@Getter
@Setter
public class ActivateBindingCommand {
    /**
     * 绑定记录标识。
     */
    private Long bindingId;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
