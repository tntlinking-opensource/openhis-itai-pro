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

/**
 * 加载体检收费明细请求（IF-PE-CHG-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "加载体检收费明细请求")
public class LoadPhysicalExamChargeItemsRequest {

    /** 体检就诊主键。 */
    @Schema(description = "体检就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者主键（可选，与就诊校验一致）。 */
    @Schema(description = "患者主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;
}
