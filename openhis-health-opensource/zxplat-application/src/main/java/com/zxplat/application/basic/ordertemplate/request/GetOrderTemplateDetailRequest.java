/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.ordertemplate.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 组套模板详情查询请求（IF-BAS-OTL-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "组套模板详情请求", requiredProperties = {"orderTemplateId"})
public class GetOrderTemplateDetailRequest {

    @Schema(description = "组套模板头 id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "组套模板 id 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 组套模板头 id。 */
    private Long orderTemplateId;
}
