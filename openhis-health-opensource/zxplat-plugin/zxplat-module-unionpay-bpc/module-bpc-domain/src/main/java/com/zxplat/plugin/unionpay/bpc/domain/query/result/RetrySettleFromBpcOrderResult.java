/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.domain.query.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** retrySettleFromBpcOrder 校验结果（实际 settle 仍由 health AppService 重传 clientScanResult）。 */
@Data
@Accessors(chain = true)
public class RetrySettleFromBpcOrderResult {

    /** 是否允许补结算（BPC 已 SUCCESS）。 */
    private boolean eligible;

    /** 提示文案。 */
    private String message;
}
