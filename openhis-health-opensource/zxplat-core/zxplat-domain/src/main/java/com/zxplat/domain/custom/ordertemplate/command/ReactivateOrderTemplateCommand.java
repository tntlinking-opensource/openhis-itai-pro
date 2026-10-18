/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.ordertemplate.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 重新启用已停用组套（RETIRED → ACTIVE），使模板重新参与套用。
 */
@Getter
@Setter
public class ReactivateOrderTemplateCommand {
    /**
     * 组套模板标识。
     */
    private Long orderTemplateId;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
