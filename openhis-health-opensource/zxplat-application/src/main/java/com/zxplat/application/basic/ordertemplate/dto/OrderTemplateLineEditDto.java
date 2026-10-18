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
 * 维护端组套模板行编辑 DTO（全量对齐提交项）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "组套模板行编辑项")
public class OrderTemplateLineEditDto {

    @Schema(description = "行 id，空=新增")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 是否删除。 */
    private Long lineId;

    @Schema(description = "是否删除")
    private Boolean deleted;

    /** 行类型 code。 */
    @Schema(description = "行类型 code")
    private String lineKindEnum;

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

    /** 默认天数。 */
    @Schema(description = "默认天数")
    private Integer defaultDays;

    /** 频次 id。 */
    @Schema(description = "频次 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long frequencyId;

    /** 用法 code。 */
    @Schema(description = "用法 code")
    private String routeCode;

    /** 剂量文本。 */
    @Schema(description = "剂量文本")
    private String dosageText;

    /** 默认执行科室 id。 */
    @Schema(description = "默认执行科室 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long defaultPerformOrgId;

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
