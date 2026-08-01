/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.diagnosis.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DOR-08A：新增诊断时追加个人/科室常用模板绑定请求。
 *
 * <p>归属对象由服务端按登录上下文解析：个人 → 当前用户；科室 → 当前登录科室。
 * 前端只传诊断定义与勾选标志，禁止由客户端指定 objectId。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-绑定个人/科室常用诊断请求")
public class BindCommonDiagnosisRequest {

    /** 诊断定义主键（西医单条；中医疾病侧）。 */
    @NotNull(message = "诊断定义标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊断定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long conditionDefinitionId;

    /**
     * 中医证候定义主键（可空；有值时与疾病一并绑定）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "中医证候定义主键（可空）")
    private Long syndromeDefinitionId;

    /** 是否追加到当前登录用户个人常用。 */
    @Schema(description = "是否存个人常用")
    private Boolean saveAsPersonal;

    /** 是否追加到当前登录科室常用。 */
    @Schema(description = "是否存科室常用")
    private Boolean saveAsDepartment;
}
