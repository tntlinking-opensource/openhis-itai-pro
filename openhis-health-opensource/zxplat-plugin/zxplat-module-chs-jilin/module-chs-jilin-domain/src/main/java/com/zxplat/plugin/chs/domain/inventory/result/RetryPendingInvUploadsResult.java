/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 重试待上报/失败流水汇总出参 */
@Data
@Accessors(chain = true)
public class RetryPendingInvUploadsResult {

    private int attempted;

    private int succeeded;

    private int failed;

    private int skipped;
}
