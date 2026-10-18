/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.conditiondefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 诊断定义批量项模型。
 */
@Getter
@Setter
public class ConditionDefinitionBatchItemQueryModel {
    /** 主键。 */
    private Long id;
    /** 病情编码。 */
    private String conditionCode;
    /** 国家医保诊断编码（目录 {@code yb_no}，2401/220x 平台病种码来源）。 */
    private String ybNo;
    /** 名称。 */
    private String name;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** 目录来源枚举（中西医分类判定）。 */
    private String sourceEnum;
}
