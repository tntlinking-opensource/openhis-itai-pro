/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.list.request;

import lombok.Data;

/**
 * 入账状态查询请求（P3）。
 */
@Data
public class AccountStatusRequest {

    private Long documentId;
    private String billBatchCode;
    private String billNo;
}
