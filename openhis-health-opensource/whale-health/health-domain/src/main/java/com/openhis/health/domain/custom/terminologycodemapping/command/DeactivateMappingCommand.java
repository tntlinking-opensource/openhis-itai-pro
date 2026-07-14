/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.terminologycodemapping.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 停用术语映射命令。
 */
@Getter
@Setter
public class DeactivateMappingCommand {
    /**
     * 映射记录标识。
     */
    private Long id;
    /**
     * 停用原因（可空）。
     */
    private String reason;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
