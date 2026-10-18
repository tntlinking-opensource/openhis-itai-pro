/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.payment.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/** 查单 G 结果。 */
@Data
@Accessors(chain = true)
public class ThirdPartyQueryExtensionOut {

    /** 查询成功（非交易成功）。 */
    private boolean success;
    /** 订单状态（如 SUCCESS / FAILED / PENDING）。 */
    private String status;
    /** 银联/BPC 交易号。 */
    private String tradeNo;
    /** BPC 原值 pay_type。 */
    private String payType;
}
