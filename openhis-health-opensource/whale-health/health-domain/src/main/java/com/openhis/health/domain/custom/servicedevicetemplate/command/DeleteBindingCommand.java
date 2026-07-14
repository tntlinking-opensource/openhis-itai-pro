/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.servicedevicetemplate.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 删除绑定（逻辑删）命令。
 */
@Getter
@Setter
public class DeleteBindingCommand {
    /**
     * 绑定记录标识。
     */
    private Long bindingId;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
