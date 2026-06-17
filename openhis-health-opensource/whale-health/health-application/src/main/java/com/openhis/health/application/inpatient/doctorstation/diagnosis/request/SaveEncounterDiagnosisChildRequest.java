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

package com.openhis.health.application.inpatient.doctorstation.diagnosis.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-05 保存批次单行：就诊诊断事实与关联的写入载荷（AS-IP-DOR-002）。
 *
 * <p>与 {@link SaveEncounterDiagnosisRequest#getDiagnosisChildList()} 顺序一一对应领域批量保存返回的 id 列表。</p>
 */
@Data
@Accessors(chain = true)
/** 医生站-就诊诊断保存子项。 */
@Schema(description = "医生站-就诊诊断保存子项")
public class SaveEncounterDiagnosisChildRequest {

    /**
     * 诊断事实主键：已存在行回传；空表示本次新增，由领域分配 id。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊断事实标识；空=新增")
    private Long conditionId;

    /**
     * 就诊诊断关联主键：已有关联时回传；空表示新建关联行。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊诊断关联标识；空=新增")
    private Long encounterDiagnosisId;

    /**
     * 诊断定义（目录）主键：标准编码与名称的来源锚点。
     */
    @NotNull(message = "诊断定义不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊断定义标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long conditionDefinitionId;

    /**
     * 标准诊断编码：须与所选定义一致，供 Gate 与落库校验。
     */
    @NotBlank(message = "诊断编码不能为空")
    @Schema(description = "诊断编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String conditionCode;

    /**
     * 诊断定义标准名称：目录展示主文案，保存时冗余传入便于审计。
     */
    @NotBlank(message = "诊断名称不能为空")
    @Schema(description = "诊断定义名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String conditionDefinitionName;

    /**
     * 验证状态 UI 整数码（1～6）：空则服务端按「已确认」落库。
     */
    @Schema(description = "验证状态整数码；空则按已确认处理")
    private Integer verificationStatusEnum;

    /**
     * 医保目录诊断编码或院内对码，写入病情事实。
     */
    @Schema(description = "医保编码")
    private String ybNo;

    /**
     * 主诊标记：{@code 1} 本就诊主诊断，{@code 0} 非主诊；批次内至多一条为 1。
     */
    @Schema(description = "主诊标记：1=主诊断，0=非主诊断")
    private Integer mainDiagnosisFlag;

    /**
     * 列表排序号：映射就诊诊断关联 {@code rank}，批次内不可重复。
     */
    @Schema(description = "诊断排序号")
    private Integer diagnosisSortNo;

    /**
     * 医保医疗类别编码：写入就诊诊断关联 {@code medType}；空则服务端默认 {@link com.openhis.health.domain.share.enums.YbMedType#GENERAL_OUTPATIENT}（{@code 11}）。
     */
    @Schema(description = "医疗类型代码（YbMedType code；空=普通门诊）")
    private String medicalTypeCode;

    /**
     * 诊断补充说明：写入关联描述，可回落到病情事实描述。
     */
    @Schema(description = "诊断描述")
    private String diagnosisDesc;

    /**
     * 患者疾病诊断类型码（住院扩展）：须为 {@link com.openhis.health.domain.share.enums.IptPatientDiseaseDiagType} 合法 code。
     */
    @Schema(description = "患者疾病诊断类型代码")
    private String iptDiseaseTypeCode;

    /**
     * 诊断分类字典码：写入病情事实 {@code categoryEnum}。
     */
    @Schema(description = "诊断分类字典码")
    private String categoryCode;

    /**
     * 严重程度字典码：写入病情事实 {@code severityEnum}。
     */
    @Schema(description = "严重程度字典码")
    private String severityCode;
}
