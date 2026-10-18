/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.diagnosis.response;

import com.zxplat.application.inpatient.doctorstation.diagnosis.dto.CommonDiagnosisRowDto;
import com.zxplat.framework.core.application.dto.OptionDto;
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

    /**
     * 住院患者疾病诊断类型下拉（术语 {@code ipt_dise_type_code}），供诊断表「诊断类型」列编辑。
     */
    @Schema(description = "住院患者疾病诊断类型选项（诊断类型列下拉）")
    private List<OptionDto> iptDiseaseTypeOptions;
}
