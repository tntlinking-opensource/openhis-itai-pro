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

package com.openhis.health.domain.workflow.supplyrequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建盘点执行单命令（见 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#createStocktake}）。
 *
 * <p>行字段口径：{@code itemQuantity} 盈亏（可负）、{@code totalQuantity} 实盘、{@code totalPrice} 实盘金额、
 * {@code profitLossPrice} 盈亏金额；盘点库房仅写 {@code purposeLocationId}/{@code purposeTypeEnum}（单端点）。</p>
 *
 * <p>修订：首行 {@code busNo} 非空时表示同号全量替换（软删+重插），见
 * {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#createStocktake}。</p>
 *
 * <p>枚举语义字段为持久化 {@code getCode()}（B.10.8.1）；同意后由应用层对非零盈亏行入账。</p>
 */
@Getter
@Setter
public class CreateStocktakeCommand extends BatchCommand<CreateStocktakeCommand.CreateStocktakeItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 盘点单行。
     */
    @Getter
    @Setter
    public static class CreateStocktakeItemCommand {
        /** 业务单号；修订时首行必填既有单号，新建时留空由领域生成。 */
        private String busNo;
        /** 供应类型持久化码（{@link com.openhis.health.domain.share.enums.SupplyType} 盘点类）。 */
        private String typeEnum;
        /** 物品来源表。 */
        private String itemTable;
        /** 物品定义主键。 */
        private Long itemId;
        /** 盈亏数量（拆零；盈为正、亏为负、可无差异为 0）。 */
        private BigDecimal itemQuantity;
        /** 实盘数量（拆零，≥0）。 */
        private BigDecimal totalQuantity;
        /** 计量单位编码。 */
        private String unitCode;
        /** 供应类别持久化码。 */
        private String categoryEnum;
        /** 盘点库房位置类型持久化码（单端点真源）。 */
        private String purposeTypeEnum;
        /** 盘点库房位置主键（单端点真源）。 */
        private Long purposeLocationId;
        /** 批号。 */
        private String lotNumber;
        /** 业务发生时间。 */
        private LocalDateTime occurrenceTime;
        /** 申请人主键。 */
        private Long applicantId;
        private Long practitionerId;
        /** 申请时间。 */
        private LocalDateTime applyTime;
        /** 原因说明。 */
        private String reason;
        /** 行备注（库列 {@code remake}）。 */
        private String remake;
        /** 单价（可选，展示/计价辅助）。 */
        private BigDecimal price;
        /** 实盘金额。 */
        private BigDecimal totalPrice;
        /** 盈亏金额（盈为正、亏为负）。 */
        private BigDecimal profitLossPrice;
    }
}
