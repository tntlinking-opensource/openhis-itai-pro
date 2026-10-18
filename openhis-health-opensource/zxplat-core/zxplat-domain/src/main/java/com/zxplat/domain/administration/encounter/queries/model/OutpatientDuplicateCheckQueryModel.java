/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 门诊重复挂号校验结果模型。
 */
@Getter
@Setter
public class OutpatientDuplicateCheckQueryModel {

    /**
     * 是否重复。
     */
    private Boolean duplicated;

    /**
     * 命中的就诊标识集合。
     */
    private List<Long> encounterIds;

    /**
     * 患者标识。
     */
    private Long patientId;

    /**
     * 目标科室（组织机构）标识。
     */
    private Long organizationId;
}
