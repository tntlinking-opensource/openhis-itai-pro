/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.deptissuereturn.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 可退领用行（拉取预填 IF-DEP-TL-13）。
 *
 * <p>展示列与领用明细网格对齐：名称、规格、厂家、金额、有效期、备注等，便于带入后直接回显。</p>
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

    /**
     * 行金额预览（单价 × 可退余量，计算刻度；前端金额列初值）。
     */
    @Schema(description = "行金额预览 totalPrice")
    private BigDecimal totalPrice;

    /** 产品名称 productName。 */
    @Schema(description = "产品名称 productName")
    private String productName;

    /** 规格 specification。 */
    @Schema(description = "规格 specification")
    private String specification;

    /** 厂家 manufacturer。 */
    @Schema(description = "厂家 manufacturer")
    private String manufacturer;

    /** 计量单位展示名 unitCodeDictText。 */
    @Schema(description = "计量单位展示名 unitCodeDictText")
    private String unitCodeDictText;

    /**
     * 批号效期止点（自源领用行 {@code end_time}）。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "批号效期止点 endTime")
    private LocalDateTime endTime;

    /** 源领用行备注 remake。 */
    @Schema(description = "源领用行备注 remake")
    private String remake;
}
