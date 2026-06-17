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

package com.openhis.health.application.inventory.pharmacy.stockin.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 药房入库页初始化响应（IF-PHA-IN-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
/** 药房入库页初始化 IF-PHA-IN-01。 */
@Schema(description = "药房入库页初始化 IF-PHA-IN-01")
public class LoadPharmacyStockInFormInitResponse {

    @Schema(description = "目的药房下拉")
    private List<OptionDto> pharmacyLocationOptions;

    /** 来源药库下拉。 */
    @Schema(description = "来源药库下拉")
    private List<OptionDto> warehouseLocationOptions;

    /** 入库项目类型两档。 */
    @Schema(description = "入库项目类型两档")
    private List<OptionDto> inboundItemTableTypeOptions;

    /** 单据状态下拉 SupplyStatus。 */
    @Schema(description = "单据状态下拉 SupplyStatus")
    private List<OptionDto> statusOptions;

    /** 药师人员下拉。 */
    @Schema(description = "药师人员下拉，可空")
    private List<OptionDto> pharmacistOptions;

    /** 库管人员下拉。 */
    @Schema(description = "库管人员下拉，可空")
    private List<OptionDto> warehouseKeeperOptions;
}
