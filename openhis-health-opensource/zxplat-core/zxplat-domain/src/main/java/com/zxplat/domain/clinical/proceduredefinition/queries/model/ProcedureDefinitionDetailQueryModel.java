/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.proceduredefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 手术操作定义详情读模型（DM-CLN-009 §4.6.2 / AS-SDD IF-03）。
 */
@Getter
@Setter
public class ProcedureDefinitionDetailQueryModel {
    /** 主键。 */
    private Long id;
    /** 目录主编码。 */
    private String procedureCode;
    /** 名称。 */
    private String name;
    /** 目录来源码。 */
    private String sourceEnum;
    /** 拼音检索码。 */
    private String pyStr;
    /** 五笔检索码。 */
    private String wbStr;
    /** 类别/类型编码。 */
    private String typeCode;
    /** 描述。 */
    private String description;
    /** 医保手术码。 */
    private String ybNo;
    /** 医保标记。 */
    private Boolean ybFlag;
    /** 医保对码标记。 */
    private Boolean ybMatchFlag;
    /** 国家临床版手术编码。 */
    private String clinicCode;
    /** 发布状态码。 */
    private String statusEnum;
    /**
     * 挂靠诊疗项目定义主键（计费映射；可空）。
     */
    private Long activityDefinitionId;
}
