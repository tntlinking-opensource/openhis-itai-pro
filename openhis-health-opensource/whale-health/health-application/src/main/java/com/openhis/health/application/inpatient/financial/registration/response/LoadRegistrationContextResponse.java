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
/** 住院登记上下文。 */
@Schema(description = "住院登记上下文")
public class LoadRegistrationContextResponse {

    @Schema(description = "患者主档")
    /** 患者主档（业务字段）。 */
    private RegistrationPatientDto patient;

    /** 住院就诊/申请信息；无 encounterId 时为 null。 */
    @Schema(description = "住院就诊/申请信息；无 encounterId 时为 null")
    /** 住院就诊/申请信息；无 encounterId 时为 null（业务字段）。 */
    private RegistrationEncounterDto encounter;

    /** 费用性质下拉。 */
    @Schema(description = "费用性质下拉")
    /** 费用性质下拉（业务字段）。 */
    private List<OptionDto> contractOptions;

    /** 入院科室下拉。 */
    @Schema(description = "入院科室下拉（类别=住院的启用科室）")
    /** 入院科室下拉（类别=住院的启用科室）（业务字段）。 */
    private List<OptionDto> organizationOptions;

    /** 入院方式下拉（术语 in_way_code）。 */
    @Schema(description = "入院方式下拉")
    /** 入院方式下拉（业务字段）。 */
    private List<OptionDto> inWayCodeOptions;

    /** 入院类型下拉（术语 admit_source_code）。 */
    @Schema(description = "入院类型下拉")
    /** 入院类型下拉（业务字段）。 */
    private List<OptionDto> admitSourceCodeOptions;

    /** 查看模式为 true 时不可编辑。 */
    @Schema(description = "查看模式为 true 时不可编辑")
    /** 查看模式为 true 时不可编辑（业务字段）。 */
    private Boolean readOnly;
}
