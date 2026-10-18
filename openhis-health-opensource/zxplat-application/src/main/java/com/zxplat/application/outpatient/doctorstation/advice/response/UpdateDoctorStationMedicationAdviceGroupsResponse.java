/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DOR-17：更新药品医嘱组号响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-更新药品医嘱组号响应")
public class UpdateDoctorStationMedicationAdviceGroupsResponse {

    @Schema(description = "是否成功")
    /** 提示信息。 */
    private Boolean success;

    @Schema(description = "提示信息")
    private String message;
}
