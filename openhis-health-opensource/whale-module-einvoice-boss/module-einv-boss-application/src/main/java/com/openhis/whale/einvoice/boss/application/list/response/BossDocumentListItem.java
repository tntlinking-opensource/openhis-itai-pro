/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.list.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 电子发票查询列表行。
 */
@Data
public class BossDocumentListItem {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long invoiceId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentId;

    private String patientName;
    private String issueKind;
    private String issueKindLabel;
    private BigDecimal paymentAmt;
    private String bossBillNo;
    private String invoiceStatus;
    private String amountStatus;
    private String busNo;
    private LocalDateTime issuedAt;
    private LocalDateTime createdAt;

    /** 博思冲红状态（SUCCESS/FAILED/SKIPPED，作废票可能有值）。 */
    private String writeOffStatus;

    /** 冲红状态中文。 */
    private String writeOffStatusLabel;

    /** 博思红票号码。 */
    private String writeOffBillNo;

    private LocalDateTime writeOffAt;

    /** 冲红失败原因摘要。 */
    private String writeOffMessage;
}
