/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.document.request;

import lombok.Data;

/**
 * 支付主键请求。
 */
@Data
public class PaymentIdRequest {

    private Long paymentId;
}
