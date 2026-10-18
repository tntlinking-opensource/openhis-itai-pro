/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.ordertemplate.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-BAS-OTL-08：预览组套套用明细请求。
 *
 * <p>返回结构由 {@link com.zxplat.application.basic.ordertemplate.response.PreviewOrderTemplateApplyResponse}
 * 承载；价与库位须由前端以行 id 调 IF-DOR-11 / IF-IP-PRC-01 {@code prepickedProductKeys} 补齐。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "预览组套套用请求")
public class PreviewOrderTemplateApplyRequest {

    /** 组套模板 id。 */
    @NotNull(message = "组套模板标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "组套模板 id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long orderTemplateId;

    /** 就诊主键（医生站必填）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 就诊标识。 */
    @Schema(description = "就诊标识")
    private Long encounterId;

    /** 患者主键（医生站必填）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者标识。 */
    @Schema(description = "患者标识")
    private Long patientId;

    /** 套用上下文：DOCTOR / NURSE_PRICING。 */
    @NotBlank(message = "套用上下文不能为空")
    /** 套用上下文：DOCTOR / NURSE_PRICING。 */
    @Schema(description = "套用上下文：DOCTOR / NURSE_PRICING", requiredMode = Schema.RequiredMode.REQUIRED)
    private String applyContextCode;

    /** 工作站类型：OUTPATIENT / INPATIENT。 */
    @Schema(description = "工作站类型：OUTPATIENT / INPATIENT")
    private String workstationKindCode;
}
