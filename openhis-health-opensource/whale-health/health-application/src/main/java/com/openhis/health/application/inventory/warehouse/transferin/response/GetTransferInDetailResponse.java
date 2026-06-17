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

package com.openhis.health.application.inventory.warehouse.transferin.response;

import com.openhis.health.application.inventory.warehouse.transferin.dto.TransferInDetailLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 药房入库单详情响应（IF-PHA-IN-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
/** 药房入库单详情 IF-PHA-IN-03。 */
@Schema(description = "药房入库单详情 IF-PHA-IN-03")
public class GetTransferInDetailResponse {

    @Schema(description = "业务单号")
    private String busNo;

    /** 类型 CABINET_TRANSFER_IN。 */
    @Schema(description = "类型 CABINET_TRANSFER_IN")
    private String typeEnum;

    /** 状态 SupplyStatus#getCode。 */
    @Schema(description = "状态 SupplyStatus#getCode")
    private String statusEnum;

    /** 来源位置主键。 */
    @Schema(description = "来源位置主键")
    private Long sourceLocationId;

    /** 目的位置主键。 */
    @Schema(description = "目的位置主键")
    private Long purposeLocationId;

    /** 源位置类型。 */
    @Schema(description = "源位置类型")
    private String sourceTypeEnum;

    /** 目的位置类型。 */
    @Schema(description = "目的位置类型")
    private String purposeTypeEnum;

    /** 供应类别。 */
    @Schema(description = "供应类别")
    private String categoryEnum;

    /** 关联上游业务单号。 */
    @Schema(description = "关联上游业务单号")
    private String originalBusNo;

    /** 明细行。 */
    @Schema(description = "明细行")
    private List<TransferInDetailLineDto> lines;
}
