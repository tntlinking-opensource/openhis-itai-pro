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

package com.openhis.health.application.querysupport.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 诊疗套餐子项（对齐 {@code wkf_activity_definition_children} 读模型，禁止对外透出 children_json 串）。
 */
@Getter
@Setter
@Schema(description = "诊疗套餐子项")
public class AdvicePackageChildItemDto {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "子项诊疗项目定义主键")
    private Long childrenId;

    @Schema(description = "子项数量系数")
    private Integer childrenNum;

    @Schema(description = "子项名称")
    private String childrenName;
}
