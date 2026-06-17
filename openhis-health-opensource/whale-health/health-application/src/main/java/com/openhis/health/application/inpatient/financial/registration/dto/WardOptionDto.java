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

package com.openhis.health.application.inpatient.financial.registration.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 病区下拉选项 DTO。
 */
@Data
@Accessors(chain = true)
/** 病区选项。 */
@Schema(description = "病区选项")
public class WardOptionDto {

    @Schema(description = "病区位置 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 病区位置 ID（业务字段）。 */
    private Long wardId;

    /** 病区编码。 */
    @Schema(description = "病区编码")
    /** 病区编码（业务字段）。 */
    private String wardCode;

    /** 病区名称。 */
    @Schema(description = "病区名称")
    /** 病区名称（业务字段）。 */
    private String wardName;
}
