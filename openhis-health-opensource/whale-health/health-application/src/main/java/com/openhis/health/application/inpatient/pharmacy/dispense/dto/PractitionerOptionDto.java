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

package com.openhis.health.application.inpatient.pharmacy.dispense.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 调配药师候选行（住院发药 IF-IPP-01 初始化分页）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "调配药师候选行")
public class PractitionerOptionDto {

    /** 药师主键。 */
    @Schema(description = "药师主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long practitionerId;

    /** 展示姓名。 */
    @Schema(description = "展示姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String displayName;

    /** 所属机构主键。 */
    @Schema(description = "所属机构主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;

    /** 所属机构名称。 */
    @Schema(description = "所属机构名称")
    private String orgName;
}
