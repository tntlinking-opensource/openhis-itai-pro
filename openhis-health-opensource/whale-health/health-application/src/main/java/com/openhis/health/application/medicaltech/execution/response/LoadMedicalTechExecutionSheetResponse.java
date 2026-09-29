/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.execution.response;

import com.openhis.health.application.medicaltech.execution.dto.MedicalTechExecutionLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 加载医技执行单响应（AS-MT-EXE-001 IF-MT-EXE-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "加载医技执行单响应")
public class LoadMedicalTechExecutionSheetResponse {

    /** 本站可见诊疗行。 */
    @Schema(description = "诊疗行")
    private List<MedicalTechExecutionLineDto> serviceLines;

    /** 本站可见耗材行。 */
    @Schema(description = "耗材行")
    private List<MedicalTechExecutionLineDto> deviceLines;
}
