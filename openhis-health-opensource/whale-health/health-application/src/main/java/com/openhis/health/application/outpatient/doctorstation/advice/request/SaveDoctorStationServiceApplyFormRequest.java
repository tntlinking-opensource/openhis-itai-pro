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

package com.openhis.health.application.outpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.ServiceApplyFormLineRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-21：保存检验/检查/治疗申请单（整张）请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "保存检验/检查/治疗申请单请求")
public class SaveDoctorStationServiceApplyFormRequest {

    /** 就诊主键；须与患者归属一致。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者主键；须与就诊快照一致。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 申请单类别：{@code TREATMENT}/21、{@code PROOF}/22、{@code TEST}/23。 */
    @NotNull(message = "申请单类别不能为空")
    @Schema(description = "申请单类别：TREATMENT/21、PROOF/22、TEST/23", requiredMode = Schema.RequiredMode.REQUIRED)
    private String categoryEnum;

    /** 整单执行科室；写入该单下全部 {@code ServiceRequest.org_id}。 */
    @NotNull(message = "执行科室不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "整单执行科室", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long orgId;

    /** 整单绑定诊断定义主键；与 {@link #encounterDiagnosisId} 成对落库，写入该单下全部 {@code wkf_service_request.condition_definition_id}。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "整单绑定诊断定义主键")
    private Long conditionDefinitionId;

    /** 整单绑定就诊诊断主键；写入该单下全部 {@code wkf_service_request.encounter_diagnosis_id}。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "整单绑定就诊诊断主键")
    private Long encounterDiagnosisId;

    /** 编辑时待替换的旧 {@code AR} 申请单号；新建可空。 */
    @Schema(description = "编辑时待替换的旧 AR 申请单号")
    private String previousPrescriptionNo;

    /** 明细行，至少 1 行；每行对应一条 ServiceRequest 草稿。 */
    @NotEmpty(message = "申请单明细不能为空")
    @Valid
    @Schema(description = "明细行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ServiceApplyFormLineRequest> lines;
}
