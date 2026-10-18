/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.ordertemplate.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 维护端组套模板生命周期变更请求（IF-BAS-OTL-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "组套模板生命周期变更请求", requiredProperties = {"orderTemplateId", "lifecycleActionCode"})
public class ChangeOrderTemplateLifecycleRequest {

    @Schema(description = "组套模板头 id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "组套模板 id 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 组套模板头 id。 */
    private Long orderTemplateId;

    /** 生命周期动作 code。 */
    @Schema(description = "生命周期动作 code", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "生命周期动作不能为空")
    private String lifecycleActionCode;
}
