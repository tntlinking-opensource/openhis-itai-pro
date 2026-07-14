/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicelocationtemplate.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 按开立科室加载取药规则列表请求。
 */
@Data
@Schema(description = "按开立科室查询取药规则请求", requiredProperties = {"organizationId"})
public class ListServiceLocationTemplateByOrganizationRequest {

    /** 开立科室标识。 */
    @NotNull(message = "开立科室不能为空")
    @Schema(description = "开立科室标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;
}
