/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.charge.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 体检预结算请求（IF-PE-CHG-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体检预结算请求")
public class PreSettlePhysicalExamChargesRequest {

    /** 体检就诊主键。 */
    @Schema(description = "体检就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 本次拟预结算的收费明细主键列表。 */
    @Schema(description = "收费明细主键列表")
    private List<Long> chargeItemIds;
}
