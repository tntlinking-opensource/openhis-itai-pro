/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.commonresult.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * PEIS 常见结果分页检索条件。
 */
@Getter
@Setter
public class PeisCommonResultQuery {

    /** 租户标识。 */
    private Long tenantId;

    /** 小项观察定义。 */
    private Long observationDefinitionId;

    /** 科室机构。 */
    private Long deptOrgId;

    /** 状态精确过滤。 */
    private String statusEnum;

    /** 名称关键字（display 模糊）。 */
    private String keyword;
}
