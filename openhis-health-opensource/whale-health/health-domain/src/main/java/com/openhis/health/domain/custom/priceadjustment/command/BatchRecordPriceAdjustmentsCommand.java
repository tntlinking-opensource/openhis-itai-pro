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

package com.openhis.health.domain.custom.priceadjustment.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 批量记录价格调整命令。
 */
@Getter
@Setter
public class BatchRecordPriceAdjustmentsCommand extends BatchCommand<BatchRecordPriceAdjustmentsCommand.BatchRecordPriceAdjustmentItemCommand> {
    /**
     * 操作人标识（批量条目共用）。
     */
    private Long operatorId;

    @Getter
    @Setter
    public static class BatchRecordPriceAdjustmentItemCommand {
        /**
         * 定义标识。
         */
        private Long definitionId;
        /**
         * 价格类型编码。
         */
        private String priceType;
        /**
         * 批号（可空）。
         */
        private String lotNumber;
        /**
         * 调整前金额。
         */
        private BigDecimal oldAmount;
        /**
         * 调整后金额。
         */
        private BigDecimal newAmount;
        /**
         * 调整原因编码。
         */
        private String adjustmentReason;
        /**
         * 调整时间（可空；由领域默认当前时间）。
         */
        private Date adjustmentTime;
        /**
         * 机构标识。
         */
        private Long orgId;
        /**
         * 库存数量（批号价场景必填；可空）。
         */
        private Integer stockQuantity;
    }
}

