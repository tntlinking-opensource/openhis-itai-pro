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
import java.util.List;

/**
 * 创建盘点差异发放命令（仅非零差异行落库，见 {@link com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryDomainService#createStocktakingDelivery}）。
 *
 * <p>外层 {@code typeEnum} 与差异行字段中的单位、数量等与持久化约定一致；类型枚举为 {@code getCode()}（B.10.8.1）。</p>
 */
@Getter
@Setter
public class CreateStocktakingDeliveryCommand extends BatchCommand<CreateStocktakingDeliveryCommand.CreateStocktakingDeliveryItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 盘点执行单行：可携带多条差异子行。
     */
    @Getter
    @Setter
    public static class CreateStocktakingDeliveryItemCommand {
        /** 上游供应请求行主键。 */
        private Long requestId;
        /** 发放类型持久化码。 */
        private String typeEnum;
        /** 差异明细；空或空列表时本行跳过。 */
        private List<DifferenceLineCommand> differenceLines;
    }

    /**
     * 盘点差异子行（逐条生成一条 {@link com.openhis.health.domain.workflow.supplydelivery.entity.SupplyDelivery}）。
     */
    @Getter
    @Setter
    public static class DifferenceLineCommand {
        /** 物品来源表。 */
        private String itemTable;
        /** 物品定义主键。 */
        private Long itemId;
        /** 差异数量。 */
        private BigDecimal quantity;
        /** 计量单位编码。 */
        private String unitCode;
        /** 批号。 */
        private String lotNumber;
    }
}
