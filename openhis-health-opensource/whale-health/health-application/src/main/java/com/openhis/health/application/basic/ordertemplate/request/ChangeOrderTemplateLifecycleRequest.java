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

package com.openhis.health.application.basic.ordertemplate.request;

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
    private Long orderTemplateId;

    /** 动作：PUBLISH / RETIRE / DELETE。 */
    @Schema(description = "生命周期动作 code", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "生命周期动作不能为空")
    private String lifecycleActionCode;
}
