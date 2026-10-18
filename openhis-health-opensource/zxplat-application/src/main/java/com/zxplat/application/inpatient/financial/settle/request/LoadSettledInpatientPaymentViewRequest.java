/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.settle.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 已结算单查阅请求（IF-IP-STL-07）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "已结算单查阅请求", requiredProperties = {"encounterId", "patientId"})
public class LoadSettledInpatientPaymentViewRequest {

    /** 住院就诊 ID。 */
    @NotNull(message = "就诊标识不能为空")
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者 ID。 */
    @NotNull(message = "患者标识不能为空")
    /** 患者 ID。 */
    @Schema(description = "患者 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 指定结算单 ID；缺省取最近成功住院临床结算单。 */
    @Schema(description = "指定结算单 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentId;

    /** 费用呈现模式：DETAIL / SUMMARY。 */
    @Schema(description = "费用呈现模式 code")
    private String presentationMode;
}
