/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.dailysettlement.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 体检日结清单明细行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体检日结清单明细行")
public class PhysicalExamDailySettlementLineDto {

    /**
     * 收费明细主键。
     */
    @Schema(description = "收费明细主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chargeItemId;

    /**
     * 就诊主键。
     */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /**
     * 患者主键。
     */
    @Schema(description = "患者主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /**
     * 科室名称。
     */
    @Schema(description = "科室名称")
    private String departmentName;

    /**
     * 开单人姓名。
     */
    @Schema(description = "开单人姓名")
    private String ordererName;

    /**
     * 病人姓名。
     */
    @Schema(description = "病人姓名")
    private String patientName;

    /**
     * 项目名称。
     */
    @Schema(description = "项目名称")
    private String itemName;

    /**
     * 行金额（账单 total_price）。
     */
    @Schema(description = "行金额")
    private BigDecimal amount;
}
