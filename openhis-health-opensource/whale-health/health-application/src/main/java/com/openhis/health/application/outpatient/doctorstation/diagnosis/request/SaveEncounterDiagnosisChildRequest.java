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

package com.openhis.health.application.outpatient.doctorstation.diagnosis.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 就诊诊断保存批次中的单行请求体。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-就诊诊断保存子项")
public class SaveEncounterDiagnosisChildRequest {

    /** 诊断事实主键；空表示新增。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊断事实标识；空=新增")
    private Long conditionId;

    /** 就诊诊断关联主键；空表示新增关联。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊诊断关联标识；空=新增")
    private Long encounterDiagnosisId;

    /** 诊断定义主键。 */
    @NotNull(message = "诊断定义不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊断定义标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long conditionDefinitionId;

    /** 诊断编码。 */
    @NotBlank(message = "诊断编码不能为空")
    @Schema(description = "诊断编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String conditionCode;

    /** 诊断定义标准名称（目录）。 */
    @NotBlank(message = "诊断名称不能为空")
    @Schema(description = "诊断定义名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String conditionDefinitionName;

    /** 验证状态；空则服务端默认已确认。 */
    @Schema(description = "验证状态整数码；空则按已确认处理")
    private Integer verificationStatusEnum;

    /** 医保编码。 */
    @Schema(description = "医保编码")
    private String ybNo;

    /** 主诊标记：1 主诊断，0 非主诊。 */
    @Schema(description = "主诊标记：1=主诊断，0=非主诊断")
    private Integer mainDiagnosisFlag;

    /** 排序号。 */
    @Schema(description = "诊断排序号")
    private Integer diagnosisSortNo;

    /** 医疗类型代码。 */
    @Schema(description = "医疗类型代码")
    private String medicalTypeCode;

    /** 诊断描述。 */
    @Schema(description = "诊断描述")
    private String diagnosisDesc;

    /** 患者疾病诊断类型代码。 */
    @Schema(description = "患者疾病诊断类型代码")
    private String iptDiseaseTypeCode;

    /** 诊断分类字典码。 */
    @Schema(description = "诊断分类字典码")
    private String categoryCode;

    /** 严重程度字典码。 */
    @Schema(description = "严重程度字典码")
    private String severityCode;
}
