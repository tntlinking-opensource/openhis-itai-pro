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
 * 医技费用清单一键确费请求（AS-MT-FEE-001 IF-MT-FEE-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医技费用清单一键确费请求")
public class ConfirmMedicalTechFeesRequest {

    /** 就诊主键；须与当前选中患者一致。 */
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /**
     * 就诊范围：本 IF <b>仅接受</b> {@code INPATIENT}（门诊无确费业务；传 OUTPATIENT 拒）。
     */
    @Schema(description = "就诊范围（仅 INPATIENT）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encounterScope;
}
