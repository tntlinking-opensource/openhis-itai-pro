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
 * 创建或修订退库单命令（药房退上游 / 药库下游退回等，见 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#createReturn}）。
 *
 * <p>凡 {@code typeEnum}/{@code categoryEnum}/{@code sourceTypeEnum}/{@code purposeTypeEnum} 等与库 {@code *_enum} 列对应字段，均为各枚举
 * {@link com.openhis.health.domain.share.enums.EnumInterface#getCode()} 持久化字符串（B.10.8.1）。</p>
 */
@Getter
@Setter
public class CreateReturnCommand extends BatchCommand<CreateReturnCommand.CreateReturnItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 退库单行；修订路径首行须带既有 {@code busNo}。
     */
    @Getter
    @Setter
    public static class CreateReturnItemCommand {
        /** 业务单号。 */
        private String busNo;
        /** 来源/关联业务单号（退库必填，由领域校验）。 */
        private String originalBusNo;
        /** 供应类型持久化码。 */
        private String typeEnum;
        /** 物品来源表。 */
        private String itemTable;
        /** 物品定义主键。 */
        private Long itemId;
        /** 数量。 */
        private BigDecimal itemQuantity;
        /** 计量单位编码。 */
        private String unitCode;
        /** 供应类别持久化码。 */
        private String categoryEnum;
        /** 源位置类型持久化码。 */
        private String sourceTypeEnum;
        /** 源位置主键。 */
        private Long sourceLocationId;
        /** 目标位置类型持久化码。 */
        private String purposeTypeEnum;
        /** 目标位置主键。 */
        private Long purposeLocationId;
        /** 业务发生时间。 */
        private LocalDateTime occurrenceTime;
        /** 申请人主键。 */
        private Long applicantId;
        /** 申请时间。 */
        private LocalDateTime applyTime;
        /** 原因说明。 */
        private String reason;
        /** 供应商主键（库存五元组匹配）。 */
        private Long supplierId;
        /** 批号。 */
        private String lotNumber;
        /** 追溯码。 */
        private String traceNo;
        /** 单价。 */
        private BigDecimal price;
        /** 行总价。 */
        private BigDecimal totalPrice;
    }
}
