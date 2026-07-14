/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
 * 诊疗签发后触发 {@link com.openhis.health.infrastructure.integration.lis.LisAdviceApplyExtension} /
 * {@link com.openhis.health.infrastructure.integration.pacs.PacsAdviceApplyExtension} 外发。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-签发医嘱批次请求")
public class SignInpatientDoctorStationAdviceBatchRequest {

    /** 就诊标识。 */
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
