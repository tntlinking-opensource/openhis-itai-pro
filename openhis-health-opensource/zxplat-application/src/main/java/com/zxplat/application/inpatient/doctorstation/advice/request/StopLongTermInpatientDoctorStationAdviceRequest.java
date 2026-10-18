/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.inpatient.doctorstation.advice.dto.StopLongTermAdviceLineRequest;
import com.zxplat.domain.share.constants.DoctorStationAdviceConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-20：停嘱（长期医嘱）请求；批次内每行须为 {@code LONG_TERM} 医嘱。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医生站-长期医嘱停嘱请求")
public class StopLongTermInpatientDoctorStationAdviceRequest {

    /** 就诊标识。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_ENCOUNTER_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 与就诊绑定的患者主键，由 AppService 二次校验。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_PATIENT_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者标识。 */
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 待停嘱的药品/诊疗请求行集合，按行驱动领域停嘱命令。 */
    @NotEmpty(message = DoctorStationAdviceConstants.VALIDATION_STOP_LINES_REQUIRED)
    /** 待停嘱行。 */
    @Schema(description = "待停嘱行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<StopLongTermAdviceLineRequest> lines;
}
