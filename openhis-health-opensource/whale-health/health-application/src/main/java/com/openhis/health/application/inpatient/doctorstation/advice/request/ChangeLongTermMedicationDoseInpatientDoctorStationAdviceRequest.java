/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.SaveInpatientMedicationAdviceDraftLinePayload;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * IF-IP-DOR-28：长期药品改量（停旧或已停 + 同药重开草稿 + {@code based_on} 链）。
 *
 * <p>本 IF 仅落草稿，不签发；医生须再调 IF-IP-DOR-15。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医生站-长期药品改量请求")
public class ChangeLongTermMedicationDoseInpatientDoctorStationAdviceRequest {

    /** 就诊标识。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_ENCOUNTER_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 与就诊绑定的患者主键，供可写门控。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_PATIENT_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 源长期药品申请主键（须 VERIFIED 或 STOPPED）。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_CHANGE_DOSE_SOURCE_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "源长期药品申请主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceMedicationRequestId;

    /**
     * 在用改量时的停嘱时间；空=当前时刻；源嘱已 STOPPED 时忽略。
     */
    @Schema(description = "停嘱时间（在用改量可传；已停忽略）")
    private LocalDateTime stopTime;

    /**
     * 新嘱临床字段（与暂存药品行同构）；{@code medicationDefinitionId} 必须等于源嘱药品。
     */
    @NotNull(message = DoctorStationAdviceConstants.MSG_CHANGE_DOSE_PAYLOAD_REQUIRED)
    @Schema(description = "新嘱药品载荷", requiredMode = Schema.RequiredMode.REQUIRED)
    private SaveInpatientMedicationAdviceDraftLinePayload medicationPayload;
}
