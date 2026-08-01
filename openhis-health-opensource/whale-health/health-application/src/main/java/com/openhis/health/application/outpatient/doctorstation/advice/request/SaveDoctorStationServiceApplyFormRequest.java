/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

    /** 就诊标识。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者标识。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 申请单类别：TREATMENT/21、PROOF/22、TEST/23。 */
    @NotNull(message = "申请单类别不能为空")
    @Schema(description = "申请单类别：TREATMENT/21、PROOF/22、TEST/23", requiredMode = Schema.RequiredMode.REQUIRED)
    private String categoryEnum;

    /** 整单执行科室。 */
    @NotNull(message = "执行科室不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "整单执行科室", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long orgId;

    /**
     * 整单执行次数（写入该单全部 {@code ServiceRequest.execution_count}）；空或非法时编排层回落为 1。
     */
    @Schema(description = "整单执行次数（各明细行共用）")
    private Integer executionCount;

    /** 整单绑定就诊诊断主键有序串（写入该单全部 {@code ServiceRequest.encounter_diagnosis_ids}）。 */
    @Schema(description = "整单绑定就诊诊断主键有序串")
    private String encounterDiagnosisIds;

    /** 编辑时待替换的旧 AR 申请单号。 */
    @Schema(description = "编辑时待替换的旧 AR 申请单号")
    private String previousPrescriptionNo;

    /** 明细行。 */
    @NotEmpty(message = "申请单明细不能为空")
    @Valid
    @Schema(description = "明细行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ServiceApplyFormLineRequest> lines;
}
