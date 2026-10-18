/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.settle.request;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 住院结算专用费用查询请求（IF-IP-STL-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院结算专用费用查询请求", requiredProperties = {"encounterId", "patientId", "presentationMode"})
public class QueryInpatientSettleChargesRequest {

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

    /** 费用呈现模式：DETAIL / SUMMARY。 */
    @NotBlank(message = "费用呈现模式不能为空")
    /** 费用呈现模式 code。 */
    @Schema(description = "费用呈现模式 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String presentationMode;

    /** 费用业务日起。 */
    @Schema(description = "费用业务日起")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    private LocalDate chargeDateFrom;

    /** 费用业务日止。 */
    @Schema(description = "费用业务日止")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    private LocalDate chargeDateTo;
}
