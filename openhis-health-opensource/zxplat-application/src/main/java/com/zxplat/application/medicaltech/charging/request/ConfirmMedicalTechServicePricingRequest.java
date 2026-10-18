/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.charging.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.medicaltech.charging.dto.MedicalTechServicePricingWriteItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-MT-CHG-07：确认医技诊疗划价（generateSource=medical-tech-pricing）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "确认医技诊疗划价")
public class ConfirmMedicalTechServicePricingRequest {

    /** 住院就诊主键。 */
    @Schema(description = "住院就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 可选挂账账户；空则用就诊默认账户。 */
    @Schema(description = "挂账账户主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 诊疗划价写行（至少一行）。 */
    @Schema(description = "诊疗划价写行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<MedicalTechServicePricingWriteItemDto> items;
}
