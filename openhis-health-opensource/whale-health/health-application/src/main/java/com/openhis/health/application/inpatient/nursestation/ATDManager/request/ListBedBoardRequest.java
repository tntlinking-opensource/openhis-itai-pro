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

package com.openhis.health.application.inpatient.nursestation.ATDManager.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-NUR-ATD-01：床位列表查询入参（与 001 左侧树范围一致）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "床位列表查询请求", requiredProperties = {"panel"})
public class ListBedBoardRequest {

    /** 病区位置主键；与 {@code houseId} 同时存在时以病房为准。 */
    @Schema(description = "病区位置主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long wardId;

    /** 病房位置主键；优先于 {@code wardId} 收窄范围。 */
    @Schema(description = "病房位置主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long houseId;

    /** 床列表面板：{@code admission} 入科 / {@code discharge} 出（{@link com.openhis.health.domain.share.constants.InpatientNurseStationConstants}）。 */
    @NotBlank(message = "床列表面板不能为空")
    @Schema(description = "床列表面板：admission 入科 / discharge 出", requiredMode = Schema.RequiredMode.REQUIRED)
    private String panel;
}
