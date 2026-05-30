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

package com.openhis.health.domain.workflow.activitydefinition.queries.model;

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
    /** childrenJSON。 */
    private String childrenJson;
    /** children标志。 */
    private Boolean childrenFlag;
}

