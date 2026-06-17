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

import com.openhis.health.application.inventory.warehouse.stockout.dto.StockOutDetailLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 *
 * <p>头字段取自同号多行写模型的首行约定列；类型与状态字段为持久化 {@code code}。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class GetStockOutDetailResponse {

    @Schema(description = "业务单号")
    private String busNo;

    @Schema(description = "类型 CABINET_STOCK_OUT")
    private String typeEnum;

    /** 单据状态持久化身份，{@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()}。 */
    @Schema(description = "状态")
    private String statusEnum;

    /** 源药库位置主键。 */
    @Schema(description = "源位置主键")
    private Long sourceLocationId;

    /** 目的位置主键。 */
    @Schema(description = "目的位置主键")
    private Long purposeLocationId;

    /** 源位置类型持久化身份。 */
    @Schema(description = "源位置类型")
    private String sourceTypeEnum;

    /** 目的位置类型持久化身份。 */
    @Schema(description = "目的位置类型")
    private String purposeTypeEnum;

    /** 供应类别持久化身份。 */
    @Schema(description = "供应类别")
    private String categoryEnum;

    /** 关联上游业务单号。 */
    @Schema(description = "关联上游单号")
    private String originalBusNo;

    /** 同号出库明细行列表（保序）。 */
    @Schema(description = "明细行")
    private List<StockOutDetailLineDto> lines;
}
