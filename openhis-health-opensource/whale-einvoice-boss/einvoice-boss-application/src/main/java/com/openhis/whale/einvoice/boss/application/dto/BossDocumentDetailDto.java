package com.openhis.whale.einvoice.boss.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.whale.einvoice.boss.contracts.dto.BossLineDto;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 单据详情（金额调整页 / 详情抽屉）。
 */
@Data
public class BossDocumentDetailDto {

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

    private BossTaskSummaryDto task;
    private List<BossLineDto> lines = new ArrayList<>();
    private List<BossAdjustLogDto> adjustLogs = new ArrayList<>();
}
