/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.refund.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊退费页初始化请求（IF-REF-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊退费页初始化请求")
public class LoadOutpatientRefundPageInitRequest {

    /** 占位字段，当前版本不使用（业务字段）。 */
    @Schema(description = "占位字段，当前版本不使用")
    private String placeholder;
}
