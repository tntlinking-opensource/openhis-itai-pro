/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 到检前更换选定套餐响应。
 */
@Getter
@Setter
public class ChangeRosterSelectedPackageResponse {

    /** 名册主键。 */
    private Long rosterId;

    /** 体检就诊主键（不变）。 */
    private Long encounterId;

    /** 新选定套餐 ActivityDefinition 主键。 */
    private Long selectedPackageActivityId;
}
