/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.conditiondefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 诊断定义分页项模型。
 */
@Getter
@Setter
public class DiseasePageItemQueryModel {
    /** 主键。 */
    private Long id;
    /** 病情编码。 */
    private String conditionCode;
    /** 名称。 */
    private String name;
    /** source枚举编码。 */
    private String sourceEnum;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** 医保标志。 */
    private Boolean ybFlag;
    /** 医保编码。 */
    private String ybNo;
    /** 说明。 */
    private String description;
}
