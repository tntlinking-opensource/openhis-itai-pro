/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.inboundreceipt.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量扫码身份反查请求（IF-TRC-21，进货场景嵌入）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "批量扫码身份反查 IF-TRC-21", requiredProperties = {"traceNos"})
public class BatchResolveTraceIdentityRequest {

    /** 追溯码列表。 */
    @NotEmpty(message = "追溯码列表不能为空")
    @Schema(description = "追溯码列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> traceNos;
}
