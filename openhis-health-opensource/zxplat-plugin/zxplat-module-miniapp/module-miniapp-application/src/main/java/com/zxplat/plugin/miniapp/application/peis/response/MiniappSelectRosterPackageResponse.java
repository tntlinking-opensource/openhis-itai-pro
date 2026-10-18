/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.miniapp.application.peis.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 小程序：选定 / 换套餐响应。
 */
@Getter
@Setter
public class MiniappSelectRosterPackageResponse {

    private Long rosterId;
    private Long encounterId;
    private String busNo;
    private Long selectedPackageActivityId;
}
