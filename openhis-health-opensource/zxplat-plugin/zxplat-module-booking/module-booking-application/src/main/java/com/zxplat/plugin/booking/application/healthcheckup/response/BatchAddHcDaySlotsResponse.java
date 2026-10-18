/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 批量加档响应。
 */
@Getter
@Setter
public class BatchAddHcDaySlotsResponse {

    /** 新创建档位数。 */
    private Integer createdCount;

    /** 已存在而跳过的档位数。 */
    private Integer skippedCount;
}
