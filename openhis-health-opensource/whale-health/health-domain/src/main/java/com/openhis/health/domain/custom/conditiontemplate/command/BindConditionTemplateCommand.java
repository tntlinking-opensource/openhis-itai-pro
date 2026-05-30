/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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

