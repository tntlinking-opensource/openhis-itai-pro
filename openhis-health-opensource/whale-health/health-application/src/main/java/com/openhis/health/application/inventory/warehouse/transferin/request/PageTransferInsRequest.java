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

package com.openhis.health.application.inventory.warehouse.transferin.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 药房入库单分页列表请求（IF-PHA-IN-02）。
 */
@Getter
@Setter
@Accessors(chain = true)
/** 药房入库单分页列表 IF-PHA-IN-02。 */
@Schema(description = "药房入库单分页列表 IF-PHA-IN-02")
public class PageTransferInsRequest extends AbstractHealthPageRequest {

    /** 目的药房主键；传 {@code 0} 表示岗位收窄后的全部可见药房（见 SDD §3.3）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "目的药房主键，0 表示可见范围内全部药房")
    private Long purposeLocationId;

    /** 可选，按来源药库筛选「从哪入」。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 来源药库主键。 */
    @Schema(description = "来源药库主键，可选筛选")
    private Long sourceLocationId;

    /** 单据状态 SupplyStatus#getCode。 */
    @Schema(description = "单据状态 SupplyStatus#getCode")
    private String statusEnum;

    /** 申请时间起。 */
    @Schema(description = "申请时间起")
    private LocalDateTime applyTimeFrom;

    /** 申请时间止。 */
    @Schema(description = "申请时间止")
    private LocalDateTime applyTimeTo;

    /** 单据类型。 */
    @Schema(description = "单据类型，须为 CABINET_TRANSFER_IN")
    private String typeEnum;
}
