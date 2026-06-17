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
    private Long orderTemplateId;
}
