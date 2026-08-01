/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.writeoff.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 手动冲红请求。
 */
@Data
public class ManualWriteOffRequest {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long invoiceId;

    /** 冲红原因（写入博思；可空时默认「手动冲红」）。 */
    private String reason;
}
