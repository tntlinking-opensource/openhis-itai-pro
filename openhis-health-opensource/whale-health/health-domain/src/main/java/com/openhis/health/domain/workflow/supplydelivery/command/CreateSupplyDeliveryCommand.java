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
import java.time.LocalDateTime;

/**
 * 创建供应发放命令（普通路径：进货/出库/退库/损益等，落库后即为完成态，见 {@link com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryDomainService#createSupplyDelivery}）。
 *
 * <p>{@code typeEnum}、{@code statusEnum}（由实体初始化写入）等与库表 {@code *_enum} 列对应字段一律承载各业务枚举的
 * {@link com.openhis.health.domain.share.enums.EnumInterface#getCode()} 持久化字符串（B.10.8.1），禁止排序号或整型冒充。</p>
 */
@Getter
@Setter
public class CreateSupplyDeliveryCommand extends BatchCommand<CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand> {

    /** 操作人主键（写入审计与事件载荷）。 */
    private Long operatorId;

    /**
     * 单行发放事实：关联上游 {@code wkf_supply_request.id}，并记录数量、单位、批号等。
     */
    @Getter
    @Setter
    public static class CreateSupplyDeliveryItemCommand {
        /** 上游供应请求行主键 {@code request_id}。 */
        private Long requestId;
        /** 发放类型持久化码（如药库入库发放 {@link com.openhis.health.domain.share.enums.SupplyType#getCode()}，B.10.8.1）。 */
        private String typeEnum;
        /** 物品来源表名。 */
        private String itemTable;
        /** 物品定义主键。 */
        private Long itemId;
        /** 发放数量（可正可负依业务；须非零，见实体 {@link com.openhis.health.domain.workflow.supplydelivery.entity.SupplyDelivery#assertQuantityNonZero()}）。 */
        private BigDecimal quantity;
        /** 计量单位编码。 */
        private String unitCode;
        /** 批号。 */
        private String lotNumber;
        /** 业务发生时间。 */
        private LocalDateTime occurrenceTime;
    }
}
