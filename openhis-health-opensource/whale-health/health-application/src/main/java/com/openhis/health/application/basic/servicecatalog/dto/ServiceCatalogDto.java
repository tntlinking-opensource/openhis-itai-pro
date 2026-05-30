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

/* (C)2026 */
package com.openhis.health.application.basic.servicecatalog.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 诊疗目录列表行 DTO：分页场景下由活动定义读模型映射主键、业务号、名称与分类等；字典回显字段由接口或前端填充。
 *
 */
@Data
@Accessors(chain = true)
@Schema(description = "药品/器材目录数据传输对象，用于器材目录的查询、展示、编辑等场景")
public class ServiceCatalogDto {

    @Schema(description = "id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "器材唯一业务编码", example = "MED20250225001")
    private String busNo;

    @NotBlank(message = "器材名称不能为空")
    @Schema(description = "器材名称（必填）", required = true, example = "一次性使用无菌注射器")
    private String name;

    @Schema(description = "器材分类编码（关联器材分类字典表）", example = "INJECTION001")
    private String categoryCode;
    /** 分类编码对应展示名（接口层或前端字典解析填充）。 */
    private String categoryCode_dictText;

    @Schema(description = "所属科室名称（字典自动回显字段）", example = "外科")
    private String orgId_dictText;

    @Schema(description = "器材零售单价（单位：元）", example = "0.80")
    private BigDecimal retailPrice;

    @Schema(description = "财务分类编码（关联财务科目字典）", example = "FIN_MED_003")
    private String itemTypeCode;
    /** 财务分类展示名。 */
    private String itemTypeCode_dictText;

    @Schema(description = "销售/计价单位", example = "次")
    private String packageUnitCode;
    /** 销售单位展示名。 */
    private String packageUnitCode_dictText;

    @Schema(description = "医保编码", example = "YB20250225001")
    private String ybNo;

    @Schema(description = "器材状态枚举（0=停用，1=启用）", example = "1", allowableValues = {"0", "1"})
    private Integer statusEnum;
    /** 状态展示名（启用/停用等）。 */
    private String statusEnum_enumText;
}
