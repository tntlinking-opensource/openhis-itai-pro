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

package com.openhis.health.application.inventory.pharmacy.stockin.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 药房入库单分页列表请求（IF-PHA-IN-02）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药房入库单分页列表 IF-PHA-IN-02")
public class PagePharmacyStockInsRequest extends AbstractHealthPageRequest {

    /** 目的药房主键；传 {@code 0} 表示岗位收窄后的全部可见药房（见 SDD §3.3）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "目的药房主键，0 表示可见范围内全部药房")
    private Long purposeLocationId;

    /** 可选，按来源药库筛选「从哪入」。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "来源药库主键，可选筛选")
    private Long sourceLocationId;

    @Schema(description = "单据状态 SupplyStatus#getCode")
    private String statusEnum;

    @Schema(description = "申请时间起")
    private LocalDateTime applyTimeFrom;

    @Schema(description = "申请时间止")
    private LocalDateTime applyTimeTo;

    @Schema(description = "单据类型，须为 PHARMACY_STOCK_IN")
    private String typeEnum;
}
