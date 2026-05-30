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
 * 创建或修订请领 / 药房入库类供应请求命令（见 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#createRequisition}）。
 *
 * <p>枚举语义字段均为各业务枚举 {@link com.openhis.health.domain.share.enums.EnumInterface#getCode()}（B.10.8.1）。</p>
 */
@Getter
@Setter
public class CreateRequisitionCommand extends BatchCommand<CreateRequisitionCommand.CreateRequisitionItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 新建入库壳单时若需跳过待审核直接进入终态，则写入目标 {@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()}（如药库出库同意生成的
     * {@link com.openhis.health.domain.share.enums.SupplyType#PHARMACY_STOCK_IN} 壳单须为 {@link com.openhis.health.domain.share.enums.SupplyStatus#AGREE}，AS-INV-WHS-003）。
     * 空白表示沿用领域默认初态（通常为待审核）。
     */
    private String bootstrapFinalStatusCode;

    /**
     * 请领或入库类单行。
     */
    @Getter
    @Setter
    public static class CreateRequisitionItemCommand {
        /** 业务单号；特定类型修订时首行须带既有单号。 */
        private String busNo;
        /** 来源业务单号（入库类场景等）。 */
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
        /** 供应商主键（药房入库入账等）。 */
        private Long supplierId;
        /** 批号。 */
        private String lotNumber;
        /** 追溯码。 */
        private String traceNo;
        /** 单价。 */
        private BigDecimal price;
        /** 行总价。 */
        private BigDecimal totalPrice;
        /** 业务发生时间。 */
        private LocalDateTime occurrenceTime;
        /** 申请人主键。 */
        private Long applicantId;
        private Long practitionerId;
        /** 申请时间。 */
        private LocalDateTime applyTime;
        /** 原因说明。 */
        private String reason;
    }
}
