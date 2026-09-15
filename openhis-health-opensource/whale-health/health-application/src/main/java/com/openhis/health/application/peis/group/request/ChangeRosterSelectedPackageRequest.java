/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 到检前更换选定套餐请求（BR-GRP-SEL-03）。
 */
@Getter
@Setter
public class ChangeRosterSelectedPackageRequest {

    /** 名册主键。 */
    private Long rosterId;

    /** 新选定套餐 ActivityDefinition 主键。 */
    private Long packageActivityId;
}
