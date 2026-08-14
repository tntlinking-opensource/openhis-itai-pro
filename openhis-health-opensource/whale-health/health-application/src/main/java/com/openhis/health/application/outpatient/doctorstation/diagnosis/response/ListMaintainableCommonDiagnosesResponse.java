/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.diagnosis.response;

import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.MaintainableCommonDiagnosisRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前登录上下文下可维护的个人/科室常用诊断列表。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-可维护常用诊断列表响应")
public class ListMaintainableCommonDiagnosesResponse {

    /**
     * 个人常用（PERSONAL + 当前登录用户 id）。
     */
    @Schema(description = "个人常用诊断列表")
    private List<MaintainableCommonDiagnosisRowDto> personalList = new ArrayList<>();

    /**
     * 科室常用（DEPARTMENT + 当前登录科室 id）。
     */
    @Schema(description = "科室常用诊断列表")
    private List<MaintainableCommonDiagnosisRowDto> departmentList = new ArrayList<>();
}
