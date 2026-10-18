/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.practitionerworkload.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 科室/医生工作量统计页初始化入参（IF-RPT-WKL-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室医生工作量统计页初始化 IF-RPT-WKL-01")
public class LoadPractitionerWorkloadPageInitRequest {

    /** 就诊范围 OUTPATIENT/INPATIENT。 */
    @Schema(description = "就诊范围 OUTPATIENT/INPATIENT")
    private String encounterScope;

    /** 科室主键（可选）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "科室主键（可选）")
    private Long organizationId;
}
