/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.returnmedicine.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 科室下拉选项（IF-PHA-02-01）。
 */
@Data
/** 科室下拉选项。 */
@Schema(description = "科室下拉选项")
public class DepartmentOptionDto {

    @Schema(description = "科室标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long departmentId;

    @Schema(description = "科室名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String departmentName;
}
