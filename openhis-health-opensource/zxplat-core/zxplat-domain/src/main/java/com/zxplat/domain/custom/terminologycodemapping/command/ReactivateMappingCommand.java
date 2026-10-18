/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.terminologycodemapping.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 恢复术语映射命令。
 */
@Getter
@Setter
public class ReactivateMappingCommand {
    /**
     * 映射记录标识。
     */
    private Long id;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
