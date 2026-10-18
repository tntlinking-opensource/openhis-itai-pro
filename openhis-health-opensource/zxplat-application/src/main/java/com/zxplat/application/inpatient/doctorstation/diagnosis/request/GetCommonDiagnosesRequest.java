/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.diagnosis.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-03：查询四类常用诊断快捷候选请求（AS-IP-DOR-002）。
 *
 * <p>按患者历史、医生常用、登录用户个人模板、当前科室模板四个维度聚合候选，供诊断录入面板快捷点选。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-查询诊断常用分类数据请求")
public class GetCommonDiagnosesRequest {

    /**
     * 患者主键：用于「病人历史诊断」维度去重与回溯。
     */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;
}
