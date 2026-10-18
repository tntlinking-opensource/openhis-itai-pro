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
 * IF-BAS-OTL-07：医生站可套用组套/医嘱模板列表请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站组套/医嘱模板列表请求")
public class ListDoctorStationOrderTemplateRequest {

    /** 就诊标识。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 当前患者主键。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者标识。 */
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 模板来源 Tab：MAINTAINED（组套）或 FROM_ADVICE（医嘱模板）。 */
    @NotBlank(message = "模板来源不能为空")
    /** 模板来源：MAINTAINED / FROM_ADVICE。 */
    @Schema(description = "模板来源：MAINTAINED / FROM_ADVICE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String templateSourceEnum;

    /** 组套种类 Tab：GENERAL 或 TCM。 */
    @NotBlank(message = "组套种类不能为空")
    /** 组套种类：GENERAL / TCM。 */
    @Schema(description = "组套种类：GENERAL / TCM", requiredMode = Schema.RequiredMode.REQUIRED)
    private String templateKindEnum;

    /** 名称关键字。 */
    @Schema(description = "名称关键字")
    private String searchKey;
}
