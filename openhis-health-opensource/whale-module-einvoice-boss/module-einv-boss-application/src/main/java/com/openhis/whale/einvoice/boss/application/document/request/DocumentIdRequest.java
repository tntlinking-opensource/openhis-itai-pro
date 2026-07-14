/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.document.request;

import lombok.Data;

/**
 * 单据主键请求。
 */
@Data
public class DocumentIdRequest {

    private Long documentId;
}
