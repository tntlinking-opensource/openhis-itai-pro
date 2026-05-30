/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inpatient.doctorstation.diagnosis.response;

import com.openhis.health.application.inpatient.doctorstation.diagnosis.dto.CommonDiagnosisRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-03：四类常用诊断快捷候选响应（AS-IP-DOR-002）。
 *
 * <p>四个列表相互独立，由应用服务分别查询模板与历史后聚合；允许为空列表，不为 null。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-常用诊断四类列表响应")
public class CommonDiagnosesResponse {

    /**
     * 病人历史诊断：按当前患者既往就诊去重后的目录候选。
     */
    @Schema(description = "病人历史诊断")
    private List<CommonDiagnosisRowDto> patientHistoryList;

    /**
     * 医生常用诊断：当前登录医师个人模板展开结果。
     */
    @Schema(description = "医生常用诊断")
    private List<CommonDiagnosisRowDto> doctorCommonUseList;

    /**
     * 用户个人诊断：登录用户维度的个人模板候选。
     */
    @Schema(description = "用户个人诊断")
    private List<CommonDiagnosisRowDto> userPersonalList;

    /**
     * 科室诊断：当前登录科室模板候选。
     */
    @Schema(description = "科室诊断")
    private List<CommonDiagnosisRowDto> organizationList;
}
