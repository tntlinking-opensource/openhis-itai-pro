/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.returntowarehouse.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 从已同意科室入库单拉取的退库候选行（含可退余量与源科室药房可用量）。
 */
@Getter
@Setter
public class ReturnableStockInLineDto {

    /** 物品表 itemTable。 */
    @Schema(description = "物品表 itemTable")
    private String itemTable;

    /**
     * 物品主键 itemId（JSON 字符串，防前端 Number 精度丢失导致退库行键对不上）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "物品 itemId")
    private Long itemId;

    /** 入库数量 stockInQuantity。 */
    @Schema(description = "入库数量 stockInQuantity")
    private BigDecimal stockInQuantity;

    /** 已退占用量 returnedQuantity。 */
    @Schema(description = "已退占用量 returnedQuantity")
    private BigDecimal returnedQuantity;

    /** 可退余量 returnableQuantity。 */
    @Schema(description = "可退余量 returnableQuantity")
    private BigDecimal returnableQuantity;

    /** 源科室药房可用量 sourceAvailableQuantity。 */
    @Schema(description = "源科室药房可用量 sourceAvailableQuantity")
    private BigDecimal sourceAvailableQuantity;

    /** 计量单位 unitCode。 */
    @Schema(description = "计量单位 unitCode")
    private String unitCode;

    /** 批号 lotNumber。 */
    @Schema(description = "批号 lotNumber")
    private String lotNumber;

    /** 追溯码 traceNo。 */
    @Schema(description = "追溯码 traceNo")
    private String traceNo;

    /** 单价 price。 */
    @Schema(description = "单价 price")
    private BigDecimal price;

    /** 行合计 totalPrice。 */
    @Schema(description = "行合计 totalPrice")
    private BigDecimal totalPrice;

    /** 发生时间 occurrenceTime。 */
    @Schema(description = "发生时间 occurrenceTime")
    private LocalDateTime occurrenceTime;

    /** 备注 remake。 */
    @Schema(description = "备注 remake")
    private String remake;

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
}
