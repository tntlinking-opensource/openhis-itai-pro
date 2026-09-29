/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.surgeryanesth.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * AS-IP-DOR-012 请求：GetPlannedSurgeryFields。
 */
@Data
@Accessors(chain = true)
@Schema(description = "GetPlannedSurgeryFieldsRequest")
public class GetPlannedSurgeryFieldsRequest {

    /** 就诊标识。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 申请单号。 */
    @Schema(description = "申请单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String prescriptionNo;

    /** 表单种类 SURGERY/ANESTHESIA。 */
    @Schema(description = "表单种类 SURGERY/ANESTHESIA", requiredMode = Schema.RequiredMode.REQUIRED)
    private String formKindCode;
}
