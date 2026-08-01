/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-26：改住院医嘱业务时间响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医生站-改医嘱业务时间响应")
public class ChangeInpatientDoctorStationAdviceBusinessTimeResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 提示文案。 */
    @Schema(description = "提示文案")
    private String message;
}
