/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.inboundreceipt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 批量扫码身份反查失败项（IF-TRC-21）。
 */
@Getter
@Setter
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
