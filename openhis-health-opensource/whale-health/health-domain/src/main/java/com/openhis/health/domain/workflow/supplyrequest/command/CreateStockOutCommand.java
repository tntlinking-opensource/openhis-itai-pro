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
 * 创建药库出库执行单命令（见 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#createStockOut}）。
 *
 * <p>枚举语义字段均为持久化 {@code getCode()} 字符串（B.10.8.1），与 {@link com.openhis.health.domain.workflow.supplyrequest.entity.SupplyRequest} 列对齐。</p>
 */
@Getter
@Setter
public class CreateStockOutCommand extends BatchCommand<CreateStockOutCommand.CreateStockOutItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 出库单行。
     */
    @Getter
    @Setter
    public static class CreateStockOutItemCommand {
        /** 业务单号。 */
        private String busNo;
        /** 来源/关联业务单号。 */
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
        /** 业务发生时间（制单时刻，由前端传入）。 */
        private LocalDateTime occurrenceTime;
        /** 经手人医务人员主键（由前端选择传入）。 */
        private Long practitionerId;
        /** 申请人主键（草稿保存由应用层绑定会话医务人员；提交审批时由领域再次覆盖为会话申请人）。 */
        private Long applicantId;
        /** 申请时间；草稿阶段为 null，提交审批后由领域写入当前时间。 */
        private LocalDateTime applyTime;
        /** 原因。 */
        private String reason;
        /** 行备注。 */
        private String remake;
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
