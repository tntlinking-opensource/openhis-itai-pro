/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.ordertemplate.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 移除组套模板行命令（默认逻辑删除）。
 */
@Getter
@Setter
public class RemoveOrderTemplateLineCommand {
    /**
     * 组套模板行标识。
     */
    private Long lineId;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
