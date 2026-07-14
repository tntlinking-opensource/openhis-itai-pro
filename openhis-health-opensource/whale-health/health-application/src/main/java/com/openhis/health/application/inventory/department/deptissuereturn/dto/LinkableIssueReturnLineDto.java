/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissuereturn.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 可退领用行（拉取预填 IF-DEP-TL-13）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "可退领用行")
public class LinkableIssueReturnLineDto {

    /** 物品来源表名 itemTable。 */
    @Schema(description = "物品来源表名 itemTable")
    private String itemTable;

    /** 物品定义主键 itemId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "物品定义主键 itemId")
    private Long itemId;

    /** 批号 lotNumber。 */
    @Schema(description = "批号 lotNumber")
    private String lotNumber;

    /** 追溯码 traceNo。 */
    @Schema(description = "追溯码 traceNo")
    private String traceNo;

    /** 源领用数量 issueQuantity。 */
    @Schema(description = "源领用数量 issueQuantity")
    private BigDecimal issueQuantity;

    /** 可退余量 returnableQuantity。 */
    @Schema(description = "可退余量 returnableQuantity")
    private BigDecimal returnableQuantity;

    /** 计量单位 unitCode。 */
    @Schema(description = "计量单位 unitCode")
    private String unitCode;

    /** 单价 price。 */
    @Schema(description = "单价 price")
    private BigDecimal price;

    /** 产品名称 productName。 */
    @Schema(description = "产品名称 productName")
    private String productName;

    /** 规格 specification。 */
    @Schema(description = "规格 specification")
    private String specification;

    /** 计量单位展示名 unitCodeDictText。 */
    @Schema(description = "计量单位展示名 unitCodeDictText")
    private String unitCodeDictText;
}
