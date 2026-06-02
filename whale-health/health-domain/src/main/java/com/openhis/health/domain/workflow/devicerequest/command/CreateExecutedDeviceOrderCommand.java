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

package com.openhis.health.domain.workflow.devicerequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 生成已完成请求命令（执行侧直写完成态）。
 */
@Getter
@Setter
public class CreateExecutedDeviceOrderCommand extends BatchCommand<CreateExecutedDeviceOrderCommand.CreateExecutedDeviceOrderItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 创建已执行设备医嘱项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class CreateExecutedDeviceOrderItemCommand {
        /** 生成source枚举编码。 */
        private String generateSourceEnum;
        /** 设备def主键。 */
        private Long deviceDefId;
        /** 数量。 */
        private BigDecimal quantity;
        /** 就诊主键。 */
        private Long encounterId;
        /** 患者主键。 */
        private Long patientId;
        /** 执行位置。 */
        private Long performLocation;
        /** 上级医嘱来源表（补费路径；执行绑定/直接划价可为空）。 */
        private String basedOnTable;
        /** 上级医嘱来源 ID（补费路径；执行绑定/直接划价可为空）。 */
        private Long basedOnId;
    }
}
