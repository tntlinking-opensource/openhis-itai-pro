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

package com.openhis.health.application.inventory.pharmacy.stocktake.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 药房盘点页初始化出参（IF-PHA-ST-01；仅药房位置列表）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "盘点页初始化 IF-PHA-ST-01")
public class LoadPharmacyStocktakeFormInitResponse {

    /** 药房位置选项（不含药库）。 */
    @Schema(description = "药房下拉 pharmacyLocationOptions")
    private List<OptionDto> pharmacyLocationOptions;

    /** 入库项目类型两档。 */
    @Schema(description = "入库项目类型 inboundItemTableTypeOptions")
    private List<OptionDto> inboundItemTableTypeOptions;

    /** 单据状态选项。 */
    @Schema(description = "状态下拉 statusOptions")
    private List<OptionDto> statusOptions;

    /** 药师人员选项。 */
    @Schema(description = "药师下拉 pharmacistOptions")
    private List<OptionDto> pharmacistOptions;

    /** 库管人员选项。 */
    @Schema(description = "库管下拉 warehouseKeeperOptions")
    private List<OptionDto> warehouseKeeperOptions;
}
