/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.conditiondefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 门慢门特病种码 → 已启用诊断定义匹配投影（DM-CLN-002 §6.2.8）。
 */
@Getter
@Setter
public class OpspDiseCodeDefinitionMatchQueryModel {

    /** 入参备案码（归一化后）。 */
    private String opspDiseCode;

    /** 命中诊断定义主键。 */
    private Long conditionDefinitionId;

    /** 诊断目录编码。 */
    private String conditionCode;

    /** 诊断名称。 */
    private String name;

    /** 目录来源编码。 */
    private String sourceEnum;

    /**
     * 实际命中列：{@code yb_no} / {@code condition_code}。
     */
    private String matchField;

    /**
     * 同优先级是否存在多条候选（true 时应用层应标 {@code ambiguous}）。
     */
    private boolean ambiguous;
}
