/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 就诊投影分页行查询模型。
 */
@Getter
@Setter
public class EncounterProjectionQueryModel {

    /**
     * 就诊标识。
     */
    private Long encounterId;

    /**
     * 患者姓名（当前阶段由患者标识字符串映射占位）。
     */
    private String patientName;

    /**
     * 就诊类别编码。
     */
    private String classEnum;

    /**
     * 就诊状态编码。
     */
    private String statusEnum;

    /**
     * 科室标识。
     */
    private Long organizationId;
}
