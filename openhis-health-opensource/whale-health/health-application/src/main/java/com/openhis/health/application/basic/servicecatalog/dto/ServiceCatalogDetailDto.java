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

package com.openhis.health.application.basic.servicecatalog.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 诊疗目录详情出参：覆盖活动定义主数据、医保与划价标志、计费规则及子项列表等编辑回显字段。
 */
@Data
@Schema(description = "服务目录详细信息数据传输对象")
public class ServiceCatalogDetailDto {

    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "分类编码")
    private String categoryCode;

    @Schema(description = "业务编号")
    private String busNo;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "拼音码")
    private String pyStr;

    @Schema(description = "五笔码")
    private String wbStr;

    @Schema(description = "销售/计价单位编码")
    private String packageUnitCode;

    @Schema(description = "诊疗业务版本")
    private String activityVersion;

    @Schema(description = "商品名称")
    private String merchandiseName;

    @Schema(description = "商品名拼音检索码")
    private String merchandisePyStr;

    @Schema(description = "商品名五笔检索码")
    private String merchandiseWbStr;

    @Schema(description = "默认门诊单位编码")
    private String defEncounterUnitCode;

    @Schema(description = "默认住院单位编码")
    private String defInhospitalUnitCode;

    @Schema(description = "医保标志")
    @JsonSerialize(using = ToStringSerializer.class)
    private Integer ybFlag;

    @Schema(description = "划价标记")
    @JsonSerialize(using = ToStringSerializer.class)
    private Integer pricingFlag;

    @Schema(description = "医保编码")
    private String ybNo;

    @Schema(description = "医保对码标志")
    @JsonSerialize(using = ToStringSerializer.class)
    private Integer ybMatchFlag;

    @Schema(description = "状态枚举值")
    private Integer statusEnum;

    @Schema(description = "身体部位编码")
    private String bodySiteCode;

    @Schema(description = "标本编码")
    private String specimenCode;

    @Schema(description = "描述信息")
    private String descriptionText;

    @Schema(description = "规则ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleId;
    /** 计费规则展示名（字典解析）。 */
    private String ruleId_dictText;

    @Schema(description = "删除标志")
    private String deleteFlag;

    @Schema(description = "类型枚举值")
    private Integer typeEnum;

    @Schema(description = "所属科室ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;

    @Schema(description = "所在位置ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    @Schema(description = "收费项目医保等级")
    private Integer chrgitmLv;

    @Schema(description = "允许的角色枚举值")
    private Integer permittedRoleEnum;

    /** 医保类别编码（med_chrgitm_type 等术语体系）。 */
    private String ybType;
    /** 医保类别展示名。 */
    private String ybType_dictText;

    @Schema(description = "财务类别编码")
    private String itemTypeCode;

    @Schema(description = "项目购入价")
    private BigDecimal purchasePrice;

    @Schema(description = "项目零售价")
    private BigDecimal retailPrice;

    @Schema(description = "项目最高零售价")
    private BigDecimal maximumRetailPrice;

    @Schema(description = "子项")
    private List<ServiceCatalogChildDto> children;
}
