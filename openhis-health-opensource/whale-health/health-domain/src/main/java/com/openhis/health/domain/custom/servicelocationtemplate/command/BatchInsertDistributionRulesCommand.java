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

package com.openhis.health.domain.custom.servicelocationtemplate.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/**
 * 批量插入发放规则命令。
 */
@Getter
@Setter
public class BatchInsertDistributionRulesCommand extends BatchCommand<BatchInsertDistributionRulesCommand.DistributionRuleItemCommand> {
    /** 机构主键。 */
    private Long organizationId;
    /** 操作人主键。 */
    private Long operatorId;

/**
 * 分发rule项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class DistributionRuleItemCommand {
        /** 项来源表。 */
        private String itemTable;
        /** 分发类别编码。 */
        private String distributionCategoryCode;
        /** def位置主键。 */
        private Long defLocationId;
        /** 项目录主键。 */
        private Long itemDefinitionId;
        /** method编码。 */
        private String methodCode;
        /** 开始时间。 */
        private LocalTime startTime;
        /** 结束时间。 */
        private LocalTime endTime;
        /** 优先。 */
        private Integer priority;
        /** display医嘱。 */
        private Integer displayOrder;
        /** remark。 */
        private String remark;
    }
}
