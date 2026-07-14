/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.summarydispense.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 汇总单底层发放行（IF-IPS-02；含批号/扣库字段）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "汇总单发放行明细")
public class InpatientPharmacySummaryDispenseItemDto {

    /** 患者发放行主键 {@code med_medication_dispense.id}（含批号/扣库真源字段）。 */
    @Schema(description = "发放行主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /** 发放状态 code。 */
    @Schema(description = "发放状态 code")
    private String statusEnum;

    /** 发放状态展示。 */
    @Schema(description = "发放状态展示")
    private String statusEnumText;

    /** 药品定义主键。 */
    @Schema(description = "药品定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationId;

    /** 药品名称。 */
    @Schema(description = "药品名称")
    private String medicationName;

    /** 规格展示。 */
    @Schema(description = "规格")
    private String specificationText;

    /** 发放药房/库位主键。 */
    @Schema(description = "库位主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    /** 库位名称。 */
    @Schema(description = "库位名称")
    private String locationName;

    /** 批号（可空）。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 单位 code。 */
    @Schema(description = "单位 code")
    private String unitCode;

    /** 单位展示。 */
    @Schema(description = "单位展示")
    private String unitCodeText;

    /** 就诊主键。 */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者主键。 */
    @Schema(description = "患者主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 床号展示。 */
    @Schema(description = "床号展示")
    private String bedDisplay;

    /** 已持久化追溯码（可空）。 */
    @Schema(description = "追溯码")
    private String traceNo;
}
