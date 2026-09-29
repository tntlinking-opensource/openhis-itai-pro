/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
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
    /** 器材唯一业务编码。 */
    private Long id;

    @Schema(description = "器材唯一业务编码", example = "MED20250225001")
    private String busNo;

    @NotBlank(message = "器材名称不能为空")
    /** 器材名称（必填）。 */
    @Schema(description = "器材名称（必填）", required = true, example = "一次性使用无菌注射器")
    private String name;

    /** 器材分类编码（关联器材分类字典表）。 */
    @Schema(description = "器材分类编码（关联器材分类字典表）", example = "INJECTION001")
    private String categoryCode;
    /** 器材分类编码（关联器材分类字典表）。 */
    private String categoryCode_dictText;

    /** 所属科室名称（字典自动回显字段）。 */
    @Schema(description = "所属科室名称（字典自动回显字段）", example = "外科")
    private String orgId_dictText;

    /** 器材零售单价（单位：元）。 */
    @Schema(description = "器材零售单价（单位：元）", example = "0.80")
    private BigDecimal retailPrice;

    /** 规格。 */
    @Schema(description = "规格")
    private String specification;

    /** 生产厂家。 */
    @Schema(description = "生产厂家")
    private String manufacturerText;

    /** 财务分类编码（关联财务科目字典）。 */
    @Schema(description = "财务分类编码（关联财务科目字典）", example = "FIN_MED_003")
    private String itemTypeCode;
    /** 财务分类编码（关联财务科目字典）。 */
    private String itemTypeCode_dictText;

    /** 销售/计价单位。 */
    @Schema(description = "销售/计价单位", example = "次")
    private String packageUnitCode;
    /** 销售/计价单位。 */
    private String packageUnitCode_dictText;

    /** 默认就诊单位编码。 */
    @Schema(description = "默认就诊单位编码", example = "次")
    private String defEncounterUnitCode;
    /** 默认就诊单位编码。 */
    private String defEncounterUnitCode_dictText;

    /** 医保编码。 */
    @Schema(description = "医保编码", example = "YB20250225001")
    private String ybNo;

    /** 器材状态枚举（0=停用，1=启用）。 */
    @Schema(description = "器材状态枚举（0=停用，1=启用）", example = "1", allowableValues = {"0", "1"})
    private Integer statusEnum;
    /** 器材状态枚举（0=停用，1=启用）。 */
    private String statusEnum_enumText;

    /**
     * 是否已绑定套餐子项（对齐 {@code wkf_activity_definition.children_flag} / BR-ACTDEF-006）。
     * <p>{@code true}：子表存在有效子项；{@code false}/{@code null}：未绑定子项。</p>
     */
    @Schema(description = "是否已绑定套餐子项")
    private Boolean childrenFlag;
}
