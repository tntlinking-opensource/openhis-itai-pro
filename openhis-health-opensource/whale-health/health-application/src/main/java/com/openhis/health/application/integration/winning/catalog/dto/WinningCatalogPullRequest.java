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
 * 从卫宁拉取目录的网关入参（应用层 → Gateway）。
 */
@Data
@Schema(description = "卫宁目录拉取请求")
public class WinningCatalogPullRequest {

    @NotNull
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "机构主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long organizationId;

    @Schema(description = "关键字（可选，现场按卫宁接口映射）")
    private String keyword;

    @Schema(description = "页码，从 1 开始")
    private Integer pageNo = 1;

    @Schema(description = "每页条数")
    private Integer pageSize = 500;

    @Schema(description = "增量同步：仅拉取该时间点之后变更（现场对接时映射卫宁 updatedSince）")
    private LocalDateTime updatedSince;
}
