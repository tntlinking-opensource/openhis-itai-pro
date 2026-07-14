/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-17：更新药品医嘱组号响应。
 *
 * <p>返回受影响行数或成功标识，供前端刷新组号展示列。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-更新药品医嘱组号响应")
public class UpdateInpatientDoctorStationMedicationAdviceGroupsResponse {

    @Schema(description = "是否成功")
    /** 提示信息。 */
    private Boolean success;

    @Schema(description = "提示信息")
    private String message;
}
