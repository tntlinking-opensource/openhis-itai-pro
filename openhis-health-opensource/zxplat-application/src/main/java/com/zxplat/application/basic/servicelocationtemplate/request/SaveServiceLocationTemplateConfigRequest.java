/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.servicelocationtemplate.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 整科覆盖保存取药科室配置请求（WH-DPL001「保存配置」）。
 */
@Data
@Schema(description = "整科覆盖保存取药科室配置请求", requiredProperties = {"organizationId"})
public class SaveServiceLocationTemplateConfigRequest {

    /** 开立科室标识。 */
    @NotNull(message = "开立科室不能为空")
    @Schema(description = "开立科室标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;

    /** 本次提交的完整规则列表；空列表表示清空该科室全部规则。 */
    @Valid
    /** 规则行列表。 */
    @Schema(description = "规则行列表")
    private List<SaveServiceLocationTemplateConfigItemRequest> items;
}
