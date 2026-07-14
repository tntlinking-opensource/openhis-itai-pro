/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.compensation.request;

import lombok.Data;

/**
 * 单票据主键请求。
 */
@Data
public class InvoiceIdRequest {

    /** health 票据主键。 */
    private Long invoiceId;
}
