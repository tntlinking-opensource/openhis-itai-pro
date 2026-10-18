/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 选定套餐并建单响应。
 */
@Getter
@Setter
public class SelectRosterPackageResponse {

    /** 名册主键。 */
    private Long rosterId;

    /** 体检就诊主键。 */
    private Long encounterId;

    /** 体检流水号。 */
    private String busNo;

    /** 选定套餐 ActivityDefinition 主键。 */
    private Long selectedPackageActivityId;
}
