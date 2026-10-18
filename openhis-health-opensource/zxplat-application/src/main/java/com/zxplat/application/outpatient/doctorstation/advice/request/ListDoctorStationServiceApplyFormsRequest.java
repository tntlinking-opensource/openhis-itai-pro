/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DOR-22：查询申请单列表请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "查询申请单列表请求")
public class ListDoctorStationServiceApplyFormsRequest {

    /** 就诊标识。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者标识。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 申请单类别：TREATMENT/21、PROOF/22、TEST/23。 */
    @NotNull(message = "申请单类别不能为空")
    @Schema(description = "申请单类别：TREATMENT/21、PROOF/22、TEST/23", requiredMode = Schema.RequiredMode.REQUIRED)
    private String categoryEnum;
}
