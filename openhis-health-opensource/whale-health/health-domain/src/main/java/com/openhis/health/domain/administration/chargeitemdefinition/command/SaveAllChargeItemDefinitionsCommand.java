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
 * 批量保存收费项目定义命令：按实例维度写入类型、医保类别、价类、批次与价格等。
 */
@Getter
@Setter
public class SaveAllChargeItemDefinitionsCommand extends BatchCommand<SaveAllChargeItemDefinitionsCommand.Item> {
    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 单条价表/定义项。
     */
    @Getter
    @Setter
    public static class Item {
        /** 实例表标识。 */
        private String instanceTable;
        /** 业务实例主键。 */
        private Long instanceId;
        /** 收费类型编码。 */
        private String typeCode;
        /** 医保类别或对照编码。 */
        private String ybType;
        /** 价格类型编码（如零售价、医保价）。 */
        private String priceTypeCode;
        /** 收费项展示名称（写入 {@code charge_name}）。 */
        private String chargeName;
        /** 批次或效期批次号（可空）。 */
        private String lotNo;
        /** 价格数值（元，与 {@code adm_charge_item_definition.price_value} 一致）。 */
        private BigDecimal priceValue;
    }
}
