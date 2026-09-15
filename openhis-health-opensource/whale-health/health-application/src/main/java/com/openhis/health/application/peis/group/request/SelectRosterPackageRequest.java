/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 选定套餐并建单请求（BR-GRP-SEL-02）。
 */
@Getter
@Setter
public class SelectRosterPackageRequest {

    /** 名册主键。 */
    private Long rosterId;

    /** 选定套餐 ActivityDefinition 主键。 */
    private Long packageActivityId;

    /** 机构主键（可选，默认取分组/合同）。 */
    private Long orgId;
}
