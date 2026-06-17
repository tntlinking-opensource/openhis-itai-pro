/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.basic.servicelocationtemplate.request;

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
