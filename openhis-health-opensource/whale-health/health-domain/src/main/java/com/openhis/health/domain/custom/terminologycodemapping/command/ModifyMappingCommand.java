/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.terminologycodemapping.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 修改术语映射命令。
 */
@Getter
@Setter
public class ModifyMappingCommand {
    /**
     * 映射记录标识。
     */
    private Long id;
    /**
     * 目标术语名称（可空；非空则覆盖）。
     */
    private String targetName;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
