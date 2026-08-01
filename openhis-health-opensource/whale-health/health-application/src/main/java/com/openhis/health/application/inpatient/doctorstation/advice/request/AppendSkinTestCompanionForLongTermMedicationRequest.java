/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-27：对长期皮试药快捷追加 companion「皮试检查」临嘱请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医生站-长期皮试药追加皮试检查请求")
public class AppendSkinTestCompanionForLongTermMedicationRequest {

    /** 就诊标识。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_ENCOUNTER_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 与就诊绑定的患者主键，由 AppService 二次校验。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_PATIENT_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /**
     * 待追加皮试检查的长期皮试药 {@code med_medication_request.id} 列表；
     * 须 {@code therapy=LONG_TERM} 且 {@code skinTestFlag=true}。
     */
    @NotEmpty(message = DoctorStationAdviceConstants.MSG_APPEND_SKIN_TEST_MED_IDS_REQUIRED)
    @Schema(description = "长期皮试药主键列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> medicationRequestIds;
}
