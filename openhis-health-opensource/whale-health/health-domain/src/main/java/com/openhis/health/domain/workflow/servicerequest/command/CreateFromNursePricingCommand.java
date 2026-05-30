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

package com.openhis.health.domain.workflow.servicerequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 护士划价建单命令。
 */
@Getter
@Setter
public class CreateFromNursePricingCommand extends BatchCommand<CreateFromNursePricingCommand.CreateFromNursePricingItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 创建来源护理计价项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class CreateFromNursePricingItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 活动主键。 */
        private Long activityId;
        /** 数量。 */
        private BigDecimal quantity;
        /** unit编码。 */
        private String unitCode;
        /** 患者主键。 */
        private Long patientId;
        /** requester主键。 */
        private Long requesterId;
        /** 执行位置。 */
        private Long performLocation;
        /** 上级医嘱来源表（补费路径；直接划价须为空）。 */
        private String basedOnTable;
        /** 上级医嘱来源 ID（补费路径；直接划价须为空）。 */
        private Long basedOnId;
    }
}

