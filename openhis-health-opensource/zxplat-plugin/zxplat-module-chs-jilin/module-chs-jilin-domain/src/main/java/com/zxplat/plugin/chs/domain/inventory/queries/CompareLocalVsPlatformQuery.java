/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.queries;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** 本地流水与平台查询（3508–3513）差异比对请求 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class CompareLocalVsPlatformQuery extends QueryPlatformInvQuery {

    /** 平台查询交易号：3508 | 3509 | 3510 | 3511 | 3512 | 3513 */
    private String compareQueryInfno;
}
