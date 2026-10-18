/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.inpatient.doctorstation.advice.dto.CancelStopLongTermAdviceLineRequest;
import com.zxplat.domain.share.constants.DoctorStationAdviceConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-25：取消停嘱（长期医嘱）请求；批次内每行须为 {@code LONG_TERM} 且 {@code STOPPED}。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医生站-长期医嘱取消停嘱请求")
public class CancelStopLongTermInpatientDoctorStationAdviceRequest {

    /** 就诊主键。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_ENCOUNTER_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者主键。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_PATIENT_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 待取消停嘱行。 */
    @NotEmpty(message = DoctorStationAdviceConstants.VALIDATION_CANCEL_STOP_LINES_REQUIRED)
    @Schema(description = "待取消停嘱行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<CancelStopLongTermAdviceLineRequest> lines;
}
