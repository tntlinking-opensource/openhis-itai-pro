/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.activitydefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 诊疗项目基础信息查询模型（详情主体）。
 */
@Getter
@Setter
public class ActivityDefinitionBaseQueryModel {
    /** 主键。 */
    private Long id;
    /** 类别编码。 */
    private String categoryCode;
    /** 业务号。 */
    private String busNo;
    /** 名称。 */
    private String name;
    /** pystr。 */
    private String pyStr;
    /** wbstr。 */
    private String wbStr;
    /** 销售/计价单位编码。 */
    private String packageUnitCode;
    /** 诊疗业务版本。 */
    private String activityVersion;
    /** 商品名称。 */
    private String merchandiseName;
    /** 商品名拼音检索码。 */
    private String merchandisePyStr;
    /** 商品名五笔检索码。 */
    private String merchandiseWbStr;
    /** 默认门诊单位编码。 */
    private String defEncounterUnitCode;
    /** 默认住院单位编码。 */
    private String defInhospitalUnitCode;
    /** 类型枚举编码。 */
    private String typeEnum;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** description文本。 */
    private String descriptionText;
    /** 身体部位编码。 */
    private String bodySiteCode;
    /** specimen编码。 */
    private String specimenCode;
    /** 机构主键。 */
    private Long orgId;
    /** 位置主键。 */
    private Long locationId;
    /** rule主键。 */
    private Integer ruleId;
    /** 计价标志。 */
    private Boolean pricingFlag;
    /** 医保标志。 */
    private Boolean ybFlag;
    /** 医保号。 */
    private String ybNo;
    /** 医保匹配标志。 */
    private Boolean ybMatchFlag;
    /** chrgitmlv。 */
    private Integer chrgitmLv;
    /** 病案结算类型编码。 */
    private String mrSettlementTypeCode;
    /** childrenJSON。 */
    private String childrenJson;
    /**
     * 是否已绑定套餐子项（{@code children_flag}，与子表有效行一致，BR-ACTDEF-006）。
     */
    private Boolean childrenFlag;
    /** 适用性别枚举编码。 */
    private String permittedGenderEnum;
    /** 目录展示顺序。 */
    private Integer sortOrder;
}
