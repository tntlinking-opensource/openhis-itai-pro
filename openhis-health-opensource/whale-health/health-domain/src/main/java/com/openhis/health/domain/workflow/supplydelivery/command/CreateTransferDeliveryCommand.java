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

package com.openhis.health.domain.workflow.supplydelivery.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 创建调拨发放命令（源减 + 目增双侧 {@link com.openhis.health.domain.workflow.supplydelivery.entity.SupplyDelivery}，见
 * {@link com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryDomainService#createTransferDelivery}）。
 *
 * <p>{@code typeEnum} 为供应/发放侧类型持久化 {@code getCode()}（B.10.8.1）。</p>
 */
@Getter
@Setter
public class CreateTransferDeliveryCommand extends BatchCommand<CreateTransferDeliveryCommand.CreateTransferDeliveryItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 调拨发放单行（领域拆成出库负数量与入库正数量两条记录）。
     */
    @Getter
    @Setter
    public static class CreateTransferDeliveryItemCommand {
        /** 上游供应请求行主键。 */
        private Long requestId;
        /** 发放类型持久化码。 */
        private String typeEnum;
        /** 源位置主键。 */
        private Long sourceLocationId;
        /** 目标位置主键。 */
        private Long targetLocationId;
        /** 物品定义主键。 */
        private Long itemId;
        /** 调拨数量（绝对值语义由领域解释为正数量，出库侧取负）。 */
        private BigDecimal quantity;
    }
}
