/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 维护名册可选套餐请求。
 */
@Getter
@Setter
public class UpdateRosterOptionalPackagesRequest {

    /** 名册主键。 */
    private Long rosterId;

    /** 新的可选套餐 ActivityDefinition 主键列表。 */
    private List<Long> packageActivityIds;
}
