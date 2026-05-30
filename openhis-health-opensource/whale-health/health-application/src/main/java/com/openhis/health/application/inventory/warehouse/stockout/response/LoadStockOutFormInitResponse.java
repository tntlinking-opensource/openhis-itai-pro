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

package com.openhis.health.application.inventory.warehouse.stockout.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 *
 * <p>各下拉 {@code value} 为持久化主键或枚举 {@code code}，与进货等模块 Option 约定一致（B.10.8.1）。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class LoadStockOutFormInitResponse {

    /** 源药库位置选项。 */
    @Schema(description = "源药库下拉")
    private List<OptionDto> warehouseLocationOptions;

    /** 目的药房位置选项。 */
    @Schema(description = "目的药房下拉")
    private List<OptionDto> pharmacyLocationOptions;

    /** 明细物品物理表类型两档（药品表 / 耗材表）。 */
    @Schema(description = "入库项目类型两档")
    private List<OptionDto> inboundItemTableTypeOptions;

    /** 供应状态筛选项，元素 value 为 {@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()}。 */
    @Schema(description = "单据状态下拉")
    private List<OptionDto> statusOptions;

    /** 经手药师人员选项。 */
    @Schema(description = "药师下拉")
    private List<OptionDto> pharmacistOptions;

    /** 库管岗位人员选项。 */
    @Schema(description = "库管下拉")
    private List<OptionDto> warehouseKeeperOptions;
}
