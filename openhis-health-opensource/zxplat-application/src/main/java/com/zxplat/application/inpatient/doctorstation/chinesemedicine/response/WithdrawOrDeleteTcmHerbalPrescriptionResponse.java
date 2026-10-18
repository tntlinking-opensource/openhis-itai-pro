/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.chinesemedicine.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-TCM-38：签退/删除中药处方响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "签退/删除中药处方响应")
public class WithdrawOrDeleteTcmHerbalPrescriptionResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;
}
