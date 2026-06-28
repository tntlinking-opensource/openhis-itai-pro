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

package com.openhis.health.application.finance.priceadjustment.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 可选调价项目行（IF-PRC-04 选品搜索结果）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "可选调价项目行")
public class AdjustableItemRowDto {

    /** 项目物理表。 */
    @Schema(description = "项目物理表")
    private String itemTable;

    /** 项目定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "项目定义主键")
    private Long itemId;

    /** 院内项目编码（药品/耗材/诊疗 busNo；医疗服务 busNo）。 */
    @Schema(description = "院内项目编码")
    private String itemCode;

    /** 项目名称。 */
    @Schema(description = "项目名称")
    private String itemName;

    /** 规格（药品/耗材 totalVolume；诊疗可为空）。 */
    @Schema(description = "规格")
    private String specification;

    /** 厂家/产地（物流项目 enrich；诊疗可为空）。 */
    @Schema(description = "厂家/产地")
    private String manufacturer;

    /** 项目种类展示名（目录 category 字典回填）。 */
    @Schema(description = "项目种类展示名")
    private String categoryCodeDictText;

    /** 单位展示名（物流默认包装单位；诊疗默认开立单位）。 */
    @Schema(description = "单位展示名")
    private String unitName;

    /** 全院可用库存数量总和（仅物流 {@code itemTable} 回填；与 IF-PRC-05 可用口径一致）。 */
    @Schema(description = "可用库存数量总和")
    private BigDecimal quantity;

    /** 目录当前进价（ACTIVE {@code PURCHASE}，每包装；仅物流口径回填）。 */
    @Schema(description = "目录当前进价")
    private BigDecimal currentPurchasePrice;

    /** 目录当前售价（ACTIVE {@code SALE}；物流/诊疗均回填）。 */
    @Schema(description = "目录当前售价")
    private BigDecimal currentSellingPrice;
}
