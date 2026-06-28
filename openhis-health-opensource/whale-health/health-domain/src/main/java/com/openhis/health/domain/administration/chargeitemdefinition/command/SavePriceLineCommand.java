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

package com.openhis.health.domain.administration.chargeitemdefinition.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 批量保存价格行命令：对已有或新建定义写入价类、批次与金额（支持按主键更新）。
 */
@Getter
@Setter
public class SavePriceLineCommand extends BatchCommand<SavePriceLineCommand.Item> {
    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 单条价格行。
     */
    @Getter
    @Setter
    public static class Item {
        /** 收费项目定义主键；空表示新建由领域分配。 */
        private Long id;
        /** 实例表标识。 */
        private String instanceTable;
        /** 业务实例主键。 */
        private Long instanceId;
        /** 财务类型编码（fin_type_code）；缺省时领域层从同实例历史定价行继承。 */
        private String typeCode;
        /** 医保类别或对照编码（可空）。 */
        private String ybType;
        /** 价格类型编码。 */
        private String priceTypeCode;
        /** 批次号（可空）。 */
        private String lotNo;
        /** 价格数值（元）。 */
        private BigDecimal priceValue;

        /**
         * 发布状态持久化码；新建行缺省时领域层落 {@code ACTIVE}。
         */
        private String statusEnum;
    }
}
