/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 入院诊断项：住院就诊诊断关联（可复用门诊病情事实）。
 */
@Getter
@Setter
public class AdmissionConditionItem {

    /** 已有病情事实主键。 */
    private Long conditionId;

    /** 新开诊断时必填：诊断目录主键。 */
    private Long conditionDefinitionId;

    /** 是否主诊断。 */
    private Boolean primaryFlag;

    /** 诊断说明。 */
    private String description;

    /** 是否中医诊断（中医一病一症成组提交时为 true）。 */
    private Boolean tcmFlag;

    /** 中医病证组号（同组病名与证候共用；可空由编排层分配序列号）。 */
    private String tcmSyndromeGroup;
}
