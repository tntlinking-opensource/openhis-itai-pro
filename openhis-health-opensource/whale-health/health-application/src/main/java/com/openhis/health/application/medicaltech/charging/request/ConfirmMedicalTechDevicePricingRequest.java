/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.charging.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.medicaltech.charging.dto.MedicalTechDevicePricingWriteItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 确认医技耗材划价请求（AS-MT-CHG-001 IF-MT-CHG-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "确认医技耗材划价请求")
public class ConfirmMedicalTechDevicePricingRequest {

    /** 住院就诊主键。 */
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 账户主键（可空；空则服务端按就诊默认挂账户回填）。 */
    @Schema(description = "账户主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 划价行（至少一行）。 */
    @Schema(description = "划价行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<MedicalTechDevicePricingWriteItemDto> items;
}
