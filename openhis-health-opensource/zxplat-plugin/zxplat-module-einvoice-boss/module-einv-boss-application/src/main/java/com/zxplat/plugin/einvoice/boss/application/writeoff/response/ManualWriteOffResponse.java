/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.writeoff.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 手动冲红结果。
 */
@Data
public class ManualWriteOffResponse {

    private boolean success;
    private String message;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long invoiceId;

    private String busNo;
    /** 红票号。 */
    private String writeOffBillNo;
    /** 红票批次号。 */
    private String writeOffBatchCode;

    /** 冲销重开后的新草稿票据主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reissueInvoiceId;
}
