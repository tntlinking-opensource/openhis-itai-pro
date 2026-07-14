/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.conditiondefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 医生站「新增诊断」弹窗分页行：诊断定义检索结果（读侧投影）。
 */
@Getter
@Setter
public class DoctorStationConditionDefinitionRowModel {
    /** 诊断定义主键。 */
    private Long id;
    /** 诊断编码。 */
    private String conditionCode;
    /** 诊断名称。 */
    private String name;
    /** 拼音检索码。 */
    private String pyStr;
    /** 五笔检索码。 */
    private String wbStr;
    /** 目录来源分类编码（字符串持久化值）。 */
    private String sourceEnum;
    /** 发布状态编码。 */
    private String statusEnum;
    /** 医保目录标志。 */
    private Boolean ybFlag;
    /** 医保诊断编码（目录）。 */
    private String ybNo;
}
