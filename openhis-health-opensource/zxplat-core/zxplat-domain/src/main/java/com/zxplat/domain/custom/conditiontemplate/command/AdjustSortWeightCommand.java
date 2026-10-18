/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.conditiontemplate.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 调整排序权重命令。
 */
@Getter
@Setter
public class AdjustSortWeightCommand {
    /**
     * 模板行主键。
     */
    private Long templateId;
    /**
     * 新的排序权重（非负）。
     */
    private Integer sortWeight;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
