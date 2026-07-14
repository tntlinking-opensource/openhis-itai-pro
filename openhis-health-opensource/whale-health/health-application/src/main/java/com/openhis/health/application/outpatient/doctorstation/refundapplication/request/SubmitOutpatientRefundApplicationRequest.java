/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.refundapplication.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.outpatient.doctorstation.refundapplication.dto.RefundApplicationLineItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DRA-03：提交门诊退费申请。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-申请退费-提交门诊退费申请请求")
public class SubmitOutpatientRefundApplicationRequest {

    /** 就诊主键。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 提交行集合。 */
    @NotEmpty(message = "退费申请行不能为空")
    @Valid
    /** 与 IF-DRA-01 返回成套的提交行集合。 */
    @Schema(description = "与 IF-DRA-01 返回成套的提交行集合", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<RefundApplicationLineItemDto> lines;
}
