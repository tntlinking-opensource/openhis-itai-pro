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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 新建或更新知识库条目。
 */
@Data
@Accessors(chain = true)
@Schema(description = "急诊知识库维护-保存请求")
public class SaveEmgKnowledgeBaseItemRequest {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "主键；空表示新建")
    private Long id;

    @NotBlank
    @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "拼音码")
    private String pinyinCode;

    @NotNull
    @Schema(description = "建议分级 1～5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer triageLevel;

    @NotBlank
    @Schema(description = "CHIEF_COMPLAINT 或 SYMPTOM", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(description = "分类编码，默认 OTHER")
    private String category;

    @Schema(description = "排序号；新建时可空，由服务端递增")
    private Integer sortOrder;

    @Schema(description = "0 禁用 / 1 启用，默认启用")
    private Integer status;
}
