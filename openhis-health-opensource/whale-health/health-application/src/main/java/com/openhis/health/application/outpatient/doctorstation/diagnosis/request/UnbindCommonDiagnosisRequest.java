/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.diagnosis.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 解绑个人/科室常用诊断模板请求。
 *
 * <p>仅允许解绑归属当前登录用户（个人）或当前登录科室（科室）的模板行；全院绑定不在本接口范围。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-解绑常用诊断请求")
public class UnbindCommonDiagnosisRequest {

    /**
     * 待解绑的常用模板行主键列表。
     */
    @NotEmpty(message = "模板行主键不能为空")
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    @Schema(description = "常用模板行主键列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> templateIds;
}
