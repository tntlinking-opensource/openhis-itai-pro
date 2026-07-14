/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.terminologycodemapping.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 删除术语映射命令。
 */
@Getter
@Setter
public class DeleteMappingCommand {
    /**
     * 映射记录标识。
     */
    private Long id;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
