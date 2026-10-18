/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.servicecatalog.dto;

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
    /** 分类编码。 */
    private Long id;

    @Schema(description = "分类编码")
    private String categoryCode;

    /** 业务编号。 */
    @Schema(description = "业务编号")
    private String busNo;

    /** 名称。 */
    @Schema(description = "名称")
    private String name;

    /** 拼音码。 */
    @Schema(description = "拼音码")
    private String pyStr;

    /** 五笔码。 */
    @Schema(description = "五笔码")
    private String wbStr;

    /** 销售/计价单位编码。 */
    @Schema(description = "销售/计价单位编码")
    private String packageUnitCode;

    /** 诊疗业务版本。 */
    @Schema(description = "诊疗业务版本")
    private String activityVersion;

    /** 商品名称。 */
    @Schema(description = "商品名称")
    private String merchandiseName;

    /** 商品名拼音检索码。 */
    @Schema(description = "商品名拼音检索码")
    private String merchandisePyStr;

    /** 商品名五笔检索码。 */
    @Schema(description = "商品名五笔检索码")
    private String merchandiseWbStr;

    /** 默认门诊单位编码。 */
    @Schema(description = "默认门诊单位编码")
    private String defEncounterUnitCode;

    /** 默认住院单位编码。 */
    @Schema(description = "默认住院单位编码")
    private String defInhospitalUnitCode;

    /** 医保标志。 */
    @Schema(description = "医保标志")
    @JsonSerialize(using = ToStringSerializer.class)
    private Integer ybFlag;

    /** 划价标记。 */
    @Schema(description = "划价标记")
    @JsonSerialize(using = ToStringSerializer.class)
    private Integer pricingFlag;

    /** 医保编码。 */
    @Schema(description = "医保编码")
    private String ybNo;

    /** 医保对码标志。 */
    @Schema(description = "医保对码标志")
    @JsonSerialize(using = ToStringSerializer.class)
    private Integer ybMatchFlag;

    /** 状态枚举值。 */
    @Schema(description = "状态枚举值")
    private Integer statusEnum;

    /** 身体部位编码。 */
    @Schema(description = "身体部位编码")
    private String bodySiteCode;

    /** 标本编码。 */
    @Schema(description = "标本编码")
    private String specimenCode;

    /** 描述信息。 */
    @Schema(description = "描述信息")
    private String descriptionText;

    /** 规则ID。 */
    @Schema(description = "规则ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleId;
    /** 规则ID。 */
    private String ruleId_dictText;

    /** 删除标志。 */
    @Schema(description = "删除标志")
    private String deleteFlag;

    /** 业务类型（术语 activity_type_code 码值）。 */
    @Schema(description = "业务类型（字典 activity_type_code）")
    private String typeEnum;

    /** 所属科室ID。 */
    @Schema(description = "所属科室ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;

    /** 所在位置ID。 */
    @Schema(description = "所在位置ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    /** 收费项目医保等级。 */
    @Schema(description = "收费项目医保等级")
    private Integer chrgitmLv;

    /** 病案结算类型编码（术语 mr_settlement_type_code）。 */
    @Schema(description = "病案结算类型编码")
    private String mrSettlementTypeCode;

    /** 允许的角色枚举值。 */
    @Schema(description = "允许的角色枚举值")
    private Integer permittedRoleEnum;

    /** 允许的角色枚举值。 */
    private String ybType;
    /** 允许的角色枚举值。 */
    private String ybType_dictText;

    /** 财务类别编码。 */
    @Schema(description = "财务类别编码")
    private String itemTypeCode;

    /**
     * 进价。
     * <p>诊疗目录维护页不展示；详情可回填存量 PURCHASE 行（若有）。</p>
     */
    @Schema(description = "进价（诊疗目录维护页不使用）")
    private BigDecimal purchasePrice;

    /** 售价。 */
    @Schema(description = "售价")
    private BigDecimal retailPrice;

    /** 项目最高零售价。 */
    @Schema(description = "项目最高零售价")
    private BigDecimal maximumRetailPrice;

    /**
     * 是否已绑定套餐子项（1=是，0=否；对齐 {@code children_flag} / BR-ACTDEF-006）。
     * <p>与 {@link #children} 列表同向：有有效子项时为 1，无子项时为 0。</p>
     */
    @Schema(description = "是否已绑定套餐子项（1=是，0=否）")
    private Integer childrenFlag;

    /** 子项。 */
    @Schema(description = "子项")
    private List<ServiceCatalogChildDto> children;

    /** 编辑业务码。 */
    @Schema(description = "编辑业务码")
    private String editabilityCode;

    /** 编辑业务码说明。 */
    @Schema(description = "编辑业务码说明")
    private String editabilityMessage;
}
