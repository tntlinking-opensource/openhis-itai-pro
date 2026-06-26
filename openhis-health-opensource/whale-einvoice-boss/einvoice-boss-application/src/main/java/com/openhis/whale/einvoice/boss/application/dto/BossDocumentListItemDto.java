package com.openhis.whale.einvoice.boss.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 电子发票查询列表行。
 */
@Data
public class BossDocumentListItemDto {

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
}
