/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.conditiondefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 诊断定义详情查询模型。
 */
@Getter
@Setter
public class DiseaseDetailQueryModel {
    /** 主键。 */
    private Long id;
    /** 病情编码。 */
    private String conditionCode;
    /** 名称。 */
    private String name;
    /** 类型编码。 */
    private String sourceEnum;
    /** 拼音检索码。 */
    private String pyStr;
    /** 五笔检索码。 */
    private String wbStr;
    /** 诊断分类字典码（中医/西医等）。 */
    private String typeCode;
    /** description。 */
    private String description;
    /** 医保号。 */
    private String ybNo;
    /** 是否纳入医保目录。 */
    private Boolean ybFlag;
    /** 医保匹配标志。 */
    private Boolean ybMatchFlag;
    /** 国家临床版诊断编码（国临码）。 */
    private String clinicCode;
    /** 状态枚举编码。 */
    private String statusEnum;
}
