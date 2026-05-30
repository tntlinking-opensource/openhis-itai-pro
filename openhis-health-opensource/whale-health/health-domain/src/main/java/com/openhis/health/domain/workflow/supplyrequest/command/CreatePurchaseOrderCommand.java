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
 * 创建或修订药库订货单命令（领域入口 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#createPurchaseOrder}）。
 *
 * <p>凡 {@code typeEnum}/{@code categoryEnum}/{@code sourceTypeEnum}/{@code purposeTypeEnum} 等与库 {@code *_enum} 列对应字段，一律为各业务枚举
 * {@link com.openhis.health.domain.share.enums.EnumInterface#getCode()} 持久化字符串（B.10.8.1）。</p>
 */
@Getter
@Setter
public class CreatePurchaseOrderCommand extends BatchCommand<CreatePurchaseOrderCommand.CreatePurchaseOrderItemCommand> {

    /** 操作人主键（事件与审计）。 */
    private Long operatorId;

    /**
     * 订货单行：新建时 {@code busNo} 可空由领域生成；修订时首行须带既有单号。
     */
    @Getter
    @Setter
    public static class CreatePurchaseOrderItemCommand {
        /** 业务单号；修订路径必填。 */
        private String busNo;
        /** 供应类型持久化码（通常为药库订货 {@link com.openhis.health.domain.share.enums.SupplyType#CABINET_PURCHASE_ORDER}）。 */
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
        /** 供应商主键。 */
        private Long supplierId;
        /** 期望/业务时间（制单时刻，由前端传入）。 */
        private LocalDateTime occurrenceTime;
        /** 经手人医务人员主键（由前端选择传入）。 */
        private Long practitionerId;
        /** 申请人主键（草稿保存由应用层绑定会话医务人员；提交审批时由领域再次覆盖为会话申请人）。 */
        private Long applicantId;
        /** 申请时间；草稿阶段为 null，提交审批后由领域写入当前时间。 */
        private LocalDateTime applyTime;
        /** 原因说明。 */
        private String reason;
        /** 行备注（落库 {@code remake}）。 */
        private String remake;
        /** 单价。 */
        private BigDecimal price;
        /** 行总价。 */
        private BigDecimal totalPrice;
    }
}
