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

package com.openhis.health.application.inventory.warehouse.inventorydetail.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 现库底部汇总入参（IF-INV-DTL-03）；筛选字段与分页查询同构，不含分页键。
 */
@Getter
@Setter
@Accessors(chain = true)
/** 现库库存明细汇总 IF-INV-DTL-03。 */
@Schema(description = "现库库存明细汇总 IF-INV-DTL-03")
public class SumInventoryDetailCurrentRequest {

    @Schema(description = "关键字：名称、批号、拼音、五笔")
    private String keyword;

    /** 药品类型编码多选。 */
    @Schema(description = "药品类型编码多选")
    private List<String> medicationCategoryCodes;

    /** 耗材类型编码多选。 */
    @Schema(description = "耗材类型编码多选")
    private List<String> deviceCategoryCodes;

    /** 库房主键多选。 */
    @Schema(description = "库房主键多选")
    private List<Long> warehouseIds;

    /** 供应商主键多选。 */
    @Schema(description = "供应商主键多选")
    private List<Long> supplierIds;

    /** 医保等级多选。 */
    @Schema(description = "医保等级多选")
    private List<Integer> chrgitmLvList;

    /** 零库存筛选。 */
    @Schema(description = "零库存筛选")
    private Boolean zeroStockOnly;

    /** 近效期窗口天数。 */
    @Schema(description = "近效期窗口天数")
    private Integer expireWithinDays;

    /** 供应状态码多选。 */
    @Schema(description = "供应状态码多选")
    private List<String> inventoryStatusEnums;
}
