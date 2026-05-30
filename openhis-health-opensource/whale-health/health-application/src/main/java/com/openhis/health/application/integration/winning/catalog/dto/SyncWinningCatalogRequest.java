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

package com.openhis.health.application.integration.winning.catalog.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 触发卫宁目录同步的应用服务入参。
 */
@Data
@Schema(description = "卫宁目录同步请求")
public class SyncWinningCatalogRequest {

    @NotNull
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "机构主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long organizationId;

    @Schema(description = "已存在记录是否覆盖更新；默认 true")
    private Boolean overwrite = Boolean.TRUE;

    @Schema(description = "关键字（透传网关）")
    private String keyword;

    @Schema(description = "页码，从 1 开始")
    private Integer pageNo = 1;

    @Schema(description = "每页条数")
    private Integer pageSize = 500;

    @Schema(description = "增量同步游标")
    private LocalDateTime updatedSince;
}
