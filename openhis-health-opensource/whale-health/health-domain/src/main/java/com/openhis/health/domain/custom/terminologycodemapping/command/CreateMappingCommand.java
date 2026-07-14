/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.terminologycodemapping.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 新建术语映射命令。
 */
@Getter
@Setter
public class CreateMappingCommand {
    /**
     * 源术语所在表名。
     */
    private String sourceItemTable;
    /**
     * 源术语编码/编号。
     */
    private String sourceItemNo;
    /**
     * 目标术语编码/编号。
     */
    private String targetItemNo;
    /**
     * 目标术语名称（可空）。
     */
    private String targetName;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
