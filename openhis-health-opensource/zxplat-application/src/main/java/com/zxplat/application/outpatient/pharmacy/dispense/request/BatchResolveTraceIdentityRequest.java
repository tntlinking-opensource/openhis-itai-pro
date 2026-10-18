/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.dispense.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量扫码身份反查请求（IF-TRC-21）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量扫码身份反查请求")
public class BatchResolveTraceIdentityRequest {

    /** 追溯码列表（单次 ≤ 50）。 */
    @NotEmpty(message = "追溯码列表不能为空")
    @Schema(description = "追溯码列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> traceCodes;

    /**
     * 场景上下文（可选）：{@code DISPENSE} / {@code INBOUND} / {@code NURSE_ASSIGN} / {@code RETURN}。
     */
    @Schema(description = "场景上下文（可选）")
    private String sceneContext;
}
