/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.domain.scan.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** abandonPendingScan 结果。 */
@Data
@Accessors(chain = true)
public class AbandonPendingScanResult {

    /** 关闭的 PENDING 条数。 */
    private int abandonedCount;
}
