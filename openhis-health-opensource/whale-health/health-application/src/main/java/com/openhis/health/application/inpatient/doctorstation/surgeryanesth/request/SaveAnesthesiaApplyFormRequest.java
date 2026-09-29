/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.surgeryanesth.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.dto.AnesthesiaApplyFormLineRequest;
import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.dto.PlannedSurgeryFieldsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * AS-IP-DOR-012 请求：SaveAnesthesiaApplyForm。
 */
@Data
@Accessors(chain = true)
@Schema(description = "SaveAnesthesiaApplyFormRequest")
public class SaveAnesthesiaApplyFormRequest {

    /** 就诊标识。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 编辑时旧申请单号。 */
    @Schema(description = "编辑时旧申请单号", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String previousPrescriptionNo;

    /** 关联手术申请单号。 */
    @Schema(description = "关联手术申请单号", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String linkedSurgeryPrescriptionNo;

    /** 申请行。 */
    @Schema(description = "申请行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<AnesthesiaApplyFormLineRequest> lines;

    /** 计划字段。 */
    @Schema(description = "计划字段", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private PlannedSurgeryFieldsDto planned;
}
