/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 住院患者药品使用明细行（一行 = 一条 MedicationDispense）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院患者药品使用-明细行")
public class InpatientPatientMedicationUsageDetailLineDto {

    /**
     * 发放行主键 {@code med_medication_dispense.id}。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "发放行主键")
    private Long dispenseId;

    /**
     * 药品定义主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品定义主键")
    private Long medicationId;

    /**
     * 药品名称。
     */
    @Schema(description = "药品名称")
    private String itemName;

    /**
     * 规格。
     */
    @Schema(description = "规格")
    private String itemSpec;

    /**
     * 发放数量（原样；退药行按域/账单符号约定）。
     */
    @Schema(description = "发放数量")
    private BigDecimal quantityValue;

    /**
     * 计量单位 code。
     */
    @Schema(description = "计量单位 code")
    private String quantityUnitCode;

    /**
     * 计量单位展示文案。
     */
    @Schema(description = "计量单位展示")
    private String quantityUnitDisplay;

    /**
     * 账单单价；缺账单可空（BR-IP-PMU-012）。
     */
    @Schema(description = "账单单价")
    private BigDecimal unitPrice;

    /**
     * 账单行合计；缺账单可空；退药通常为负。
     */
    @Schema(description = "账单行合计")
    private BigDecimal totalPrice;

    /**
     * 计划发药时间。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "计划发药时间")
    private LocalDateTime plannedDispenseTime;

    /**
     * 领域发放状态 code（{@code DispenseStatus.getCode()}）。
     */
    @Schema(description = "发放状态 code")
    private String statusEnum;

    /**
     * 展示发药状态 code：{@code PENDING}/{@code DISPENSED}/{@code RETURNED}。
     */
    @Schema(description = "展示发药状态 code")
    private String usageStatusCode;

    /**
     * 展示发药状态文案：待发 / 已发 / 已退。
     */
    @Schema(description = "展示发药状态文案")
    private String usageStatusDisplay;

    /**
     * 发放类型 code（{@code MedicationDispenseType.getCode()}）。
     */
    @Schema(description = "发放类型 code")
    private String dispenseEnum;

    /**
     * 汇总发药单号；可空，仅展示、不参与汇总分桶。
     */
    @Schema(description = "汇总单号")
    private String summaryNo;

    /**
     * 批号；可空。
     */
    @Schema(description = "批号")
    private String lotNumber;
}
