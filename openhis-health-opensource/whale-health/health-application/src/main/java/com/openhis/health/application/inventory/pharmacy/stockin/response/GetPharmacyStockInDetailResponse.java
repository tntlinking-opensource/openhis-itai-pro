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

import com.openhis.health.application.inventory.pharmacy.stockin.dto.PharmacyStockInDetailLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 药房入库单详情响应（IF-PHA-IN-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药房入库单详情 IF-PHA-IN-03")
public class GetPharmacyStockInDetailResponse {

    @Schema(description = "业务单号")
    private String busNo;

    @Schema(description = "类型 PHARMACY_STOCK_IN")
    private String typeEnum;

    @Schema(description = "状态 SupplyStatus#getCode")
    private String statusEnum;

    @Schema(description = "来源位置主键")
    private Long sourceLocationId;

    @Schema(description = "目的位置主键")
    private Long purposeLocationId;

    @Schema(description = "源位置类型")
    private String sourceTypeEnum;

    @Schema(description = "目的位置类型")
    private String purposeTypeEnum;

    @Schema(description = "供应类别")
    private String categoryEnum;

    @Schema(description = "关联上游业务单号")
    private String originalBusNo;

    @Schema(description = "明细行")
    private List<PharmacyStockInDetailLineDto> lines;
}
