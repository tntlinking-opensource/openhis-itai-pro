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

package com.openhis.health.application.emergency.emgtriage.station.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-EMG-13：知识库检索。
 */
@Data
@Accessors(chain = true)
@Schema(description = "急诊分诊-知识库检索请求")
public class SearchKnowledgeForEmergencyTriageRequest {

    @Schema(description = "名称关键字")
    private String nameHint;

    @Schema(description = "词条类型")
    private String type;

    @NotNull
    @Schema(description = "页码（从 0 开始，兼容 Spring Page）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer pageNum;

    @NotNull
    @Schema(description = "页大小", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer pageSize;

    @Schema(description = "快捷 TopN（>0 时走 TopN 而非分页）")
    private Integer topNLimit;
}
