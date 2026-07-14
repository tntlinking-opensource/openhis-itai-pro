/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-17：并组写入载荷（组号 + 组内药品 request 主键列表）。
 *
 * <p>嵌于 {@link com.openhis.health.application.inpatient.doctorstation.advice.request.UpdateInpatientDoctorStationMedicationAdviceGroupsRequest}；
 * 空组号表示拆组。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站药品医嘱并组载荷")
public class AssignMedicationGroupPayload {

    /** 并组目标组号，与药品请求 {@code group_id} 对齐。 */
    @NotNull(message = DoctorStationAdviceConstants.VALIDATION_GROUP_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "目标组号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long groupId;

    /** 待并入同组的 {@code med_medication_request} 主键列表。 */
    @NotEmpty(message = DoctorStationAdviceConstants.VALIDATION_MEDICATION_REQUEST_IDS_REQUIRED)
    /** 药品请求主键列表。 */
    @Schema(description = "药品请求主键列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> medicationRequestIds;
}
