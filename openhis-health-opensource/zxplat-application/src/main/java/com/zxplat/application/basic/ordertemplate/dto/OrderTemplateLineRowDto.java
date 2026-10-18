/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.ordertemplate.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 组套模板明细行展示 DTO。
 *
 * <p>IF-BAS-OTL-03 维护详情：经 {@code OrderTemplateLineCatalogEnrichAssembler} 回填目录/术语展示字段；
 * IF-BAS-OTL-08 预览套用：仅填结构字段（定义 id、默认数量/用法等），<strong>不</strong> enrich、无价字段。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "组套模板明细行")
public class OrderTemplateLineRowDto {

    @Schema(description = "行 id")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 行类型 code。 */
    private Long lineId;

    @Schema(description = "行类型 code")
    private String lineKindEnum;

    /** 行类型展示名。 */
    @Schema(description = "行类型展示名")
    private String lineKindEnumText;

    /** 药品定义 id。 */
    @Schema(description = "药品定义 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationDefinitionId;

    /** 诊疗定义 id。 */
    @Schema(description = "诊疗定义 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityDefinitionId;

    /** 耗材定义 id。 */
    @Schema(description = "耗材定义 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deviceDefinitionId;

    /** 目录表名。 */
    @Schema(description = "目录表名")
    private String productTableName;

    /** 目录名称。 */
    @Schema(description = "目录名称")
    private String productName;

    /** 规格。 */
    @Schema(description = "规格")
    private String productSpec;

    /** 成组键。 */
    @Schema(description = "成组键")
    private String groupKey;

    /** 套内排序。 */
    @Schema(description = "套内排序")
    private Integer sortOrder;

    /** 默认数量。 */
    @Schema(description = "默认数量")
    private BigDecimal defaultQuantity;

    /** 默认单位 code。 */
    @Schema(description = "默认单位 code")
    private String defaultUnitCode;

    /** 默认单位名。 */
    @Schema(description = "默认单位名")
    private String defaultUnitName;

    /** 默认天数。 */
    @Schema(description = "默认天数")
    private Integer defaultDays;

    /** 频次 id。 */
    @Schema(description = "频次 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long frequencyId;

    /** 频次展示。 */
    @Schema(description = "频次展示")
    private String frequencyText;

    /** 目录 categoryCode。 */
    @Schema(description = "目录 categoryCode")
    private String categoryCode;

    /** 用法 code。 */
    @Schema(description = "用法 code")
    private String routeCode;

    /** 用法展示。 */
    @Schema(description = "用法展示")
    private String routeName;

    /** 剂量文本。 */
    @Schema(description = "剂量文本")
    private String dosageText;

    /** 默认执行科室 id。 */
    @Schema(description = "默认执行科室 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long defaultPerformOrgId;

    /** 默认执行科室名。 */
    @Schema(description = "默认执行科室名")
    private String defaultPerformOrgName;

    /** 行备注。 */
    @Schema(description = "行备注")
    private String lineRemark;

    /** TCM 行付数。 */
    @Schema(description = "TCM 行付数")
    private BigDecimal chineseHerbsDoseQuantity;

    /** TCM 行代煎。 */
    @Schema(description = "TCM 行代煎")
    private Boolean sufferingFlag;
}
