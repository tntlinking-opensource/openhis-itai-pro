/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.ordertemplate.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.basic.ordertemplate.dto.AdviceSourceLineKeyDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-BAS-OTL-10：医生站另存为医嘱模板请求。
 *
 * <p>默认存为科室模板；{@code bindingEnum=PERSONAL} 时归属当前登录医务人员。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站另存医嘱模板请求")
public class SaveOrderTemplateFromAdviceRequest {

    /** 就诊标识。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者主键。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者标识。 */
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 模板名称。 */
    @NotBlank(message = "模板名称不能为空")
    /** 模板名称。 */
    @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String templateName;

    /**
     * 绑定类型：{@code DEPARTMENT}（默认）或 {@code PERSONAL}。
     *
     * <p>缺省或空白时按科室保存；个人模板 {@code object_id} 取当前登录医务人员。</p>
     */
    @Schema(description = "绑定类型：DEPARTMENT（默认）/ PERSONAL")
    private String bindingEnum;

    /** 选中源行。 */
    @NotEmpty(message = "请至少选择一条医嘱")
    @Valid
    @Schema(description = "选中源行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<AdviceSourceLineKeyDto> sourceLines;
}
