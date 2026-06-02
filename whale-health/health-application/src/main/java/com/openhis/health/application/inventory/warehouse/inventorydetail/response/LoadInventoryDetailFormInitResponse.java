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

package com.openhis.health.application.inventory.warehouse.inventorydetail.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 库存明细页初始化出参（IF-INV-DTL-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "库存明细页初始化 IF-INV-DTL-01")
public class LoadInventoryDetailFormInitResponse {

    @Schema(description = "医保等级筛选项")
    private List<OptionDto> chrgitmLvOptions;

    @Schema(description = "供应状态筛选项")
    private List<OptionDto> inventoryStatusOptions;

    @Schema(description = "药品类型筛选项")
    private List<OptionDto> medicationCategoryOptions;

    @Schema(description = "耗材类型筛选项")
    private List<OptionDto> deviceCategoryOptions;
}
