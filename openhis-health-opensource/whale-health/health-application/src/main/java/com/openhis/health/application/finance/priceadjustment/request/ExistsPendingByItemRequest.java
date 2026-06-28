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

package com.openhis.health.application.finance.priceadjustment.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 项目 pending 互斥查询请求（IF-PRC-12）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "项目 pending 互斥查询请求", requiredProperties = {"itemTable", "itemId"})
public class ExistsPendingByItemRequest {

    /** 项目物理表。 */
    @NotBlank(message = "项目表不能为空")
    @Schema(description = "项目物理表", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemTable;

    /** 项目定义主键。 */
    @NotNull(message = "项目标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "项目定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long itemId;
}
