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
 * 整科覆盖保存发药位置规则命令。
 */
@Getter
@Setter
public class SaveDistributionConfigCommand extends BatchCommand<SaveDistributionConfigCommand.DistributionRuleItemCommand> {

    /**
     * 开立科室标识。
     */
    private Long organizationId;

    /**
     * 操作人标识。
     */
    private Long operatorId;

    /**
     * 配置行子命令。
     */
    @Getter
    @Setter
    public static class DistributionRuleItemCommand {
        private String itemTable;
        private String distributionCategoryCode;
        private Long defLocationId;
        private Long itemDefinitionId;
        private String methodCode;
        private LocalTime startTime;
        private LocalTime endTime;
        private Integer priority;
        private Integer displayOrder;
        private String remark;
    }
}
