/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.ordertemplate.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 逻辑删除组套模板命令。
 */
@Getter
@Setter
public class DeleteOrderTemplateCommand {
    /**
     * 组套模板标识。
     */
    private Long orderTemplateId;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
