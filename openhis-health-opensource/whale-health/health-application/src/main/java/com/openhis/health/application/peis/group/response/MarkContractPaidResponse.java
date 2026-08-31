/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检合同单位后结收款确认响应。
 */
@Getter
@Setter
public class MarkContractPaidResponse {

    private Long contractId;

    /** 合同支付状态：UNPAID / PAID */
    private String payStatusCode;
}
