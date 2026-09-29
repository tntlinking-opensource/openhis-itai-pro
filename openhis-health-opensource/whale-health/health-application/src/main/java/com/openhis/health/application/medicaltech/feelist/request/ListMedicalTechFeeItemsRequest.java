/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.feelist.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 查询医技费用清单请求（AS-MT-FEE-001 IF-MT-FEE-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "查询医技费用清单请求")
public class ListMedicalTechFeeItemsRequest {

    /** 就诊主键；须与 encounterScope 类别一致。 */
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /**
     * 就诊类别 scope：{@code OUTPATIENT}/{@code INPATIENT}
     * （复用 {@link com.openhis.health.domain.share.constants.MedicalTechExecutionConstants}）。
     */
    @Schema(description = "就诊类别 scope", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encounterScope;
}
