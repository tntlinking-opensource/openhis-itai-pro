/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.advice.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-25：取消停嘱（长期医嘱）批次处理结果。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医生站-长期医嘱取消停嘱响应")
public class CancelStopLongTermInpatientDoctorStationAdviceResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 提示文案。 */
    @Schema(description = "提示文案")
    private String message;
}
