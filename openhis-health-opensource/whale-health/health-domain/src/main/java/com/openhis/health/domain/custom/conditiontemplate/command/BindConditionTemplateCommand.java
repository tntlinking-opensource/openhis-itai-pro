/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.conditiontemplate.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 绑定诊断模板命令（支持单条与批量）。
 */
@Getter
@Setter
public class BindConditionTemplateCommand extends BatchCommand<BindConditionTemplateCommand.BindConditionTemplateItemCommand> {
    /**
     * 归属对象标识（个人/科室等，语义由 bindingEnum 决定）。
     */
    private Long objectId;
    /**
     * 绑定类型枚举值。
     */
    private String bindingEnum;
    /**
     * 操作人标识。
     */
    private Long operatorId;

    @Getter
    @Setter
    public static class BindConditionTemplateItemCommand {
        /**
         * 诊断定义主键。
         */
        private Long definitionId;
        /**
         * 排序权重（可空；默认 0）。
         */
        private Integer sortWeight;
        /**
         * 备注（可空）。
         */
        private String remark;
    }
}
