/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 就诊投影分页查询入参。
 */
@Getter
@Setter
public class EncounterProjectionQuery {

    /**
     * 页码（从 1 开始）。
     */
    private Integer pageNo;

    /**
     * 每页条数。
     */
    private Integer pageSize;

    /**
     * 患者标识。
     */
    private Long patientId;

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
