/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustment.dto;

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

    /** 院内项目编码。 */
    @Schema(description = "院内项目编码")
    private String itemCode;

    /** 项目名称。 */
    @Schema(description = "项目名称")
    private String itemName;

    /** 规格。 */
    @Schema(description = "规格")
    private String specification;

    /** 厂家/产地。 */
    @Schema(description = "厂家/产地")
    private String manufacturer;

    /** 项目种类展示名。 */
    @Schema(description = "项目种类展示名")
    private String categoryCodeDictText;

    /** 单位展示名。 */
    @Schema(description = "单位展示名")
    private String unitName;

    /** 可用库存数量总和。 */
    @Schema(description = "可用库存数量总和")
    private BigDecimal quantity;

    /** 目录当前进价。 */
    @Schema(description = "目录当前进价")
    private BigDecimal currentPurchasePrice;

    /** 目录当前售价。 */
    @Schema(description = "目录当前售价")
    private BigDecimal currentSellingPrice;
}
