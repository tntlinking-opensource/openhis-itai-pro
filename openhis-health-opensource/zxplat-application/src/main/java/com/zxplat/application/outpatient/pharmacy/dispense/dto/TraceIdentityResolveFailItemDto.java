/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.dispense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 单码身份反查失败项（IF-TRC-21）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "追溯码身份反查失败项")
public class TraceIdentityResolveFailItemDto {

    /** 追溯码。 */
    @Schema(description = "追溯码")
    private String traceNo;

    /** 解析状态。 */
    @Schema(description = "解析状态")
    private String resolveStatusEnum;
}
