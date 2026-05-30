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

package com.openhis.health.application.emergency.emgtriage.station.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 知识库候选展示行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "知识库候选词条行")
public class KnowledgeCandidateRowDto {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "词条主键")
    private Long id;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "拼音码")
    private String pinyinCode;

    @Schema(description = "关联分级建议")
    private Integer triageLevel;

    @Schema(description = "词条类型")
    private String type;

    @Schema(description = "分类")
    private String category;
}
