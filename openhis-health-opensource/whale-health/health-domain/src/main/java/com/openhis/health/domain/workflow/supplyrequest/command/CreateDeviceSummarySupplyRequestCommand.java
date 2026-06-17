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
 * 创建科室耗材类汇总/盘点供应请求命令（type 28，见 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#createDeviceSummarySupplyRequest}）。
 *
 * <p>枚举语义字段为持久化 {@code getCode()}（B.10.8.1）。</p>
 */
@Getter
@Setter
public class CreateDeviceSummarySupplyRequestCommand extends BatchCommand<CreateDeviceSummarySupplyRequestCommand.CreateDeviceSummarySupplyRequestItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 科室耗材汇总/盘点单行。
     */
    @Getter
    @Setter
    public static class CreateDeviceSummarySupplyRequestItemCommand {
        /** 业务单号。 */
        private String busNo;
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
        /** 库房类型持久化码。 */
        private String purposeTypeEnum;
        /** 库房主键。 */
        private Long purposeLocationId;
        /** 业务发生时间。 */
        private LocalDateTime occurrenceTime;
        /** 申请人主键。 */
        private Long applicantId;
        /** 申请时间。 */
        private LocalDateTime applyTime;
        /** 原因说明。 */
        private String reason;
    }
}
