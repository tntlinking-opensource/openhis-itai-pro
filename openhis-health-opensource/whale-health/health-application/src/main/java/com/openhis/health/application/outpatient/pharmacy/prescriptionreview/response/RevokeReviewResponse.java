/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.prescriptionreview.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 撤销点评响应（IF-PRV-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "撤销点评响应")
public class RevokeReviewResponse {

    /** 是否成功；失败走业务异常，成功固定 true。 */
    @Schema(description = "是否成功")
    private Boolean success;
}
