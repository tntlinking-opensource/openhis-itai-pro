/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.chinesemedicine.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** 单独取消已签发中药方的代煎费响应。 */
@Data
@Accessors(chain = true)
@Schema(description = "取消中药代煎费响应")
public class VoidTcmDecoctionChargeResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;
}
