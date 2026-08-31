/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicelocationtemplate.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalTime;

/**
 * 取药科室规则行展示 DTO（WH-DPL001 右侧表格）。
 */
@Data
@Schema(description = "取药科室规则行")
public class ServiceLocationTemplateDto {

    @Schema(description = "规则主键")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 规则主键。 */
    private Long id;

    @Schema(description = "开立科室标识")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;

    /** 开立科室名称。 */
    @Schema(description = "开立科室名称")
    private String organizationName;

    /** 物品表名。 */
    @Schema(description = "物品表名")
    private String itemTable;

    /** 物品表展示名。 */
    @Schema(description = "物品表展示名")
    private String itemTable_dictText;

    /** 发放类别编码。 */
    @Schema(description = "发放类别编码")
    private String distributionCategoryCode;

    /** 发放类别展示名。 */
    @Schema(description = "发放类别展示名")
    private String distributionCategoryCode_dictText;

    /** 默认发药库位标识。 */
    @Schema(description = "默认发药库位标识")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long defLocationId;

    /** 默认发药库位名称。 */
    @Schema(description = "默认发药库位名称")
    private String defLocationName;

    /** 具体物品定义标识。 */
    @Schema(description = "具体物品定义标识")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long itemDefinitionId;

    /** 具体物品定义名称。 */
    @Schema(description = "具体物品定义名称")
    private String itemDefinitionName;

    /**
     * 耗材种类编码（术语 device_type_code；通配为空）。
     */
    @Schema(description = "耗材种类编码")
    private String typeCode;

    /** 耗材种类展示名。 */
    @Schema(description = "耗材种类展示名")
    private String typeCode_dictText;

    /** 用法编码。 */
    @Schema(description = "用法编码")
    private String methodCode;

    /** 用法展示名。 */
    @Schema(description = "用法展示名")
    private String methodCode_dictText;

    /** 生效开始时间。 */
    @Schema(description = "生效开始时间")
    private LocalTime startTime;

    /** 生效结束时间。 */
    @Schema(description = "生效结束时间")
    private LocalTime endTime;

    /** 优先级（数值越小越优先）。 */
    @Schema(description = "优先级（数值越小越优先）")
    private Integer priority;

    /** 展示顺序。 */
    @Schema(description = "展示顺序")
    private Integer displayOrder;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;
}
