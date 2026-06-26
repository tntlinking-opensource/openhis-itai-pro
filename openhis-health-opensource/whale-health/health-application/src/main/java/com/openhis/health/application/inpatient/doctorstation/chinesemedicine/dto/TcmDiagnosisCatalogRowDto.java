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

package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DOR-31：中医病名/证候目录分页行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "中医病名或证候目录行")
public class TcmDiagnosisCatalogRowDto {

    /** 诊断定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊断定义主键")
    private Long definitionId;

    /** 诊断编码。 */
    @Schema(description = "诊断编码")
    private String conditionCode;

    /** 诊断名称。 */
    @Schema(description = "诊断名称")
    private String conditionName;

    /** 目录来源编码（05 病名 / 06 证候）。 */
    @Schema(description = "目录来源编码")
    private String sourceCode;

    /** 目录来源展示文案。 */
    @Schema(description = "目录来源展示文案")
    private String sourceText;
}
