/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.refundapplication.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.outpatient.doctorstation.refundapplication.dto.CancelRefundApplicationLineItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DRA-04：撤销门诊退费申请（整笔支付、不支持半取消）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-撤销退费请求")
public class CancelOutpatientRefundApplicationRequest {

    /** 就诊主键。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 撤销行集合（须覆盖所选支付下全部退款中收费行）。 */
    @NotEmpty(message = "撤销退费行不能为空")
    @Valid
    @Schema(description = "撤销行集合（支付锚点+收费行锚点）", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<CancelRefundApplicationLineItemDto> lines;
}
