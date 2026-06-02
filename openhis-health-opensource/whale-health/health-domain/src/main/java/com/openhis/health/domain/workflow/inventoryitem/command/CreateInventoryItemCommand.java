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

package com.openhis.health.domain.workflow.inventoryitem.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 新增库存项命令。
 */
@Getter
@Setter
public class CreateInventoryItemCommand extends BatchCommand<CreateInventoryItemCommand.CreateInventoryItemItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 创建库存项项命令嵌套类型。
     */
    @Getter
    @Setter
    public static class CreateInventoryItemItemCommand {
        /** 项来源表。 */
        private String itemTable;
        /** 项主键。 */
        private Long itemId;
        /** 名称。 */
        private String name;
        /** 供应商主键。 */
        private Long supplierId;
        /** unit编码。 */
        private String unitCode;
        /** 数量。 */
        private BigDecimal quantity;
        /** 批号号。 */
        private String lotNumber;
        /** 位置主键。 */
        private Long locationId;
        /** 位置store主键。 */
        private Long locationStoreId;
        /** expiration日期。 */
        private LocalDateTime expirationDate;

        /** 生产日期（与供应行 {@code start_time} / 库列 {@code production_date} 对齐）。 */
        private LocalDateTime productionDate;

        /** 物资分类持久化码（冗余自产品定义读模型 {@code category_code}）。 */
        private String categoryCode;

        /** 拼音检索码（冗余自产品定义读模型）。 */
        private String pyStr;

        /** 五笔检索码（冗余自产品定义读模型）。 */
        private String wbStr;
    }
}

