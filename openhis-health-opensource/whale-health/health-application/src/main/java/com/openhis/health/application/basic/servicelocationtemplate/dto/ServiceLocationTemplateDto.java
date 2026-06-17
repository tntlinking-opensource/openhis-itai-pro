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
    private Long id;

    @Schema(description = "开立科室标识")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;

    /** 开立科室名称。 */
    @Schema(description = "开立科室名称")
    private String organizationName;

    /** 物品表名（{@code med_medication_definition} / {@code adm_device_definition}）。 */
    @Schema(description = "物品表名")
    private String itemTable;

    /** 物品表展示名。 */
    @Schema(description = "物品表展示名")
    private String itemTable_dictText;

    /** 发放类别编码（须与目录侧 categoryCode 一致，供医生站匹配）。 */
    @Schema(description = "发放类别编码")
    private String distributionCategoryCode;

    /** 发放类别展示名（按物品表区分码表解析）。 */
    @Schema(description = "发放类别展示名")
    private String distributionCategoryCode_dictText;

    /** 默认发药库位标识。 */
    @Schema(description = "默认发药库位标识")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long defLocationId;

    /** 默认发药库位名称。 */
    @Schema(description = "默认发药库位名称")
    private String defLocationName;

    /** 具体药品/耗材定义标识；空表示该发放类别下通配。 */
    @Schema(description = "具体物品定义标识")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long itemDefinitionId;

    /** 具体物品定义名称。 */
    @Schema(description = "具体物品定义名称")
    private String itemDefinitionName;

    /** 用法编码；空表示不限用法；中草药类别展示走 {@code tcm_method_code}，其余药品走 {@code method_code}。 */
    @Schema(description = "用法编码")
    private String methodCode;

    /** 用法展示名（enrich 时按发放类别分码表解析）。 */
    @Schema(description = "用法展示名")
    private String methodCode_dictText;

    /** 当日内生效开始时间；空表示全天起点。 */
    @Schema(description = "生效开始时间")
    private LocalTime startTime;

    /** 当日内生效结束时间；空表示全天终点。 */
    @Schema(description = "生效结束时间")
    private LocalTime endTime;

    /** 优先级，数值越小越优先。 */
    @Schema(description = "优先级（数值越小越优先）")
    private Integer priority;

    /** 配置页展示顺序。 */
    @Schema(description = "展示顺序")
    private Integer displayOrder;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;
}
