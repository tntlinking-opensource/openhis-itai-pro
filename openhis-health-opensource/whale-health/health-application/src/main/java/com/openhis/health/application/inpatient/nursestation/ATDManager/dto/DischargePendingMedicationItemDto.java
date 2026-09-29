/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.ATDManager.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * IF-IP-NUR-ATD-04：出院前待发/待退药行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "出院前待发退药行")
public class DischargePendingMedicationItemDto {

    @Schema(description = "药品发放主键")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 药品发放主键。 */
    private Long dispenseId;

    /** 药品请求主键。 */
    @Schema(description = "药品请求主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationRequestId;

    /** 药品定义主键。 */
    @Schema(description = "药品定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationId;

    /** 药品名称。 */
    @Schema(description = "药品名称")
    private String medicationName;

    /** 发放状态 code。 */
    @Schema(description = "发放状态 code")
    private String statusEnum;

    /** 发放状态展示文案。 */
    @Schema(description = "发放状态展示文案")
    private String statusEnumText;

    /** 发放数量。 */
    @Schema(description = "发放数量")
    private BigDecimal quantity;

    /** 单位 code。 */
    @Schema(description = "单位 code")
    private String unitCode;

    /**
     * 单位展示名（术语 UNIT_CODE 解析；列表「单位」列优先展示本字段）。
     */
    @Schema(description = "单位展示名")
    private String unitCodeText;

    /** 计划发药时间。 */
    @Schema(description = "计划发药时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime plannedDispenseTime;

    /** 发药库房位置主键。 */
    @Schema(description = "发药库房位置主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long performLocationId;

    /** 发药库房名称。 */
    @Schema(description = "发药库房名称")
    private String performLocationName;
}
