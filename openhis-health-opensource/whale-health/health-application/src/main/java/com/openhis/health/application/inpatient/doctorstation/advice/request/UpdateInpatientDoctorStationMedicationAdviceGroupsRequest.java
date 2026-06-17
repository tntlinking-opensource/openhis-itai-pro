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

package com.openhis.health.application.inpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.AssignMedicationGroupPayload;
import io.swagger.v3.oas.annotations.media.Schema;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-17：更新药品医嘱组号请求（并组/拆组）。
 *
 * <p>载荷见 {@link com.openhis.health.application.inpatient.doctorstation.advice.dto.AssignMedicationGroupPayload}；
 * 仅作用于 {@code med_medication_request} 草稿或已签发行。</p>
 */
@Data
@Accessors(chain = true)
/** 医生站-医嘱开立-更新药品医嘱组号请求。 */
@Schema(description = "医生站-医嘱开立-更新药品医嘱组号请求")
public class UpdateInpatientDoctorStationMedicationAdviceGroupsRequest {

    /** 当前就诊。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_ENCOUNTER_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者门控。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_PATIENT_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者标识。 */
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 需先清空组号（拆组）的药品请求 id，与 {@link #assignGroup} 可组合使用。 */
    @Schema(description = "需要拆组的药品请求主键列表")
    private List<Long> clearGroupIds;

    /** 非空时执行并组：目标组号 + 请求 id 列表。 */
    @Schema(description = "并组写入载荷")
    private AssignMedicationGroupPayload assignGroup;
}
