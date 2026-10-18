/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.document.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.plugin.einvoice.boss.domain.issue.result.BossLineResult;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 单据详情（金额调整页 / 详情抽屉）。
 */
@Data
public class BossDocumentDetail {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long invoiceId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentId;

    private String issueKind;
    private String issueKindLabel;
    private String busNo;
    private BigDecimal totalAmt;
    private BigDecimal paymentAmt;
    private String amountStatus;

    private String patientName;
    private String genderDisplay;
    private String age;
    private String encounterBusNo;
    private String invoiceStatus;

    private String bossBillNo;
    private String bossBatchCode;
    private String pictureUrl;
    private String pictureNetUrl;
    private String billQrCode;
    private LocalDateTime issuedAt;

    /** 非阻断警告码（如支付额与开票额不一致）。 */
    private String warningCode;

    /** 非阻断警告文案。 */
    private String warningMessage;

    /** 博思冲红状态（SUCCESS/FAILED/SKIPPED）。 */
    private String writeOffStatus;
    private String writeOffStatusLabel;
    private String writeOffBillNo;
    private LocalDateTime writeOffAt;
    private String writeOffMessage;
    /** 红票批次号。 */
    private String writeOffBatchCode;

    private BossTaskSummary task;
    private List<BossLineResult> lines = new ArrayList<>();
    private List<BossAdjustLog> adjustLogs = new ArrayList<>();
}
