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

package com.openhis.health.application.inpatient.nursestation.encountermanage.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.ParticipantSubmitDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 患者信息维护请求（IF-IP-NUR-04）。
 */
@Data
@Accessors(chain = true)
/** 患者信息维护请求。 */
@Schema(description = "患者信息维护请求", requiredProperties = {"encounterId"})
public class SavePatientInfoRequest {

    /** 住院就诊主键。 */
    @NotNull(message = "就诊标识不能为空")
    @Schema(description = "住院就诊主键", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 病情/优先级 code（{@code adm_encounter.priority_enum}）。 */
    @Schema(description = "病情/优先级编码")
    private String priorityEnum;

    /** 责任医护变更集。 */
    @Schema(description = "参与者变更列表")
    private List<ParticipantSubmitDto> participants;
}
