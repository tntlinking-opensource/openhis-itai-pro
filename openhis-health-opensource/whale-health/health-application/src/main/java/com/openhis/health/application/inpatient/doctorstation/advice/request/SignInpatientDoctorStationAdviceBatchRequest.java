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
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceResourceKeyDto;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-15：签发医嘱批次请求。
 *
 * <p>目标列表见 {@link InpatientDoctorStationAdviceResourceKeyDto}；住院长临均仅 sign 请求；
 * 诊疗签发后触发 {@link com.openhis.health.application.inpatient.doctorstation.advice.port.LisPacsApplyGateway} 占位。</p>
 */
@Data
@Accessors(chain = true)
/** 医生站-医嘱开立-签发医嘱批次请求。 */
@Schema(description = "医生站-医嘱开立-签发医嘱批次请求")
public class SignInpatientDoctorStationAdviceBatchRequest {

    /** 签发所属就诊。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_ENCOUNTER_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者门控，须与就诊一致。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_PATIENT_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者标识。 */
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 待签发资源键列表（表名 + 请求 id）。 */
    @NotEmpty(message = DoctorStationAdviceConstants.VALIDATION_SIGN_TARGETS_REQUIRED)
    @Valid
    /** 签发目标列表。 */
    @Schema(description = "签发目标列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<InpatientDoctorStationAdviceResourceKeyDto> signTargets;
}
