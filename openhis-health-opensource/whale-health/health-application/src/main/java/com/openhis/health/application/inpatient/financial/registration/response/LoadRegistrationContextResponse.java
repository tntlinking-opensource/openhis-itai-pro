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

package com.openhis.health.application.inpatient.financial.registration.response;

import com.openhis.health.application.inpatient.financial.registration.dto.RegistrationEncounterDto;
import com.openhis.health.application.inpatient.financial.registration.dto.RegistrationPatientDto;
import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 登记上下文加载响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院登记上下文")
public class LoadRegistrationContextResponse {

    @Schema(description = "患者主档")
    private RegistrationPatientDto patient;

    @Schema(description = "住院就诊/申请信息；无 encounterId 时为 null")
    private RegistrationEncounterDto encounter;

    @Schema(description = "费用性质下拉")
    private List<OptionDto> contractOptions;

    @Schema(description = "查看模式为 true 时不可编辑")
    private Boolean readOnly;
}
