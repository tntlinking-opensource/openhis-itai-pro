/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.compensation.request;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量票据主键请求（批量补开 P2）。
 */
@Data
public class BatchInvoiceIdsRequest {

    private List<Long> invoiceIds = new ArrayList<>();
}
