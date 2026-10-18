/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.document.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 按支付主键查询单据摘要（账单页跳转用）。
 */
@Data
public class BossDocumentBrief {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long invoiceId;
}
