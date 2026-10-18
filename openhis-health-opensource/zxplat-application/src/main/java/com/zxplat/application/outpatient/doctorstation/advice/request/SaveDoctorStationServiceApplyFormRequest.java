/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.outpatient.doctorstation.advice.dto.ServiceApplyFormLineRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
     * 整单执行次数回落值（行未传 {@code executionCount} 时使用）；空或非法时编排层回落为 1。
     * <p>新前端按行传执行次数后可省略；保留字段以兼容旧客户端。</p>
     */
    @Schema(description = "整单执行次数回落值（行未传时使用；兼容旧客户端）")
    private Integer executionCount;

    /** 整单绑定就诊诊断主键有序串（写入该单全部 {@code ServiceRequest.encounter_diagnosis_ids}）。 */
    @Schema(description = "整单绑定就诊诊断主键有序串")
    private String encounterDiagnosisIds;

    /**
     * 编辑时待替换的旧 AR 申请单号（兼容单号客户端）。
     *
     * <p>拆多张时请优先传 {@link #previousPrescriptionNos}；仅传本字段时只删这一张。</p>
     */
    @Schema(description = "编辑时待替换的旧 AR 申请单号（兼容；拆多张请用 previousPrescriptionNos）")
    private String previousPrescriptionNo;

    /**
     * 编辑时待替换的全部旧 AR 申请单号（按执行科室拆单后同窗再保存须传齐，避免孤儿草稿）。
     *
     * <p>与 {@link #previousPrescriptionNo} 并集去重；空则视为新建。</p>
     */
    @Schema(description = "编辑时待替换的全部旧 AR 申请单号（拆多张再保存必传）")
    private List<String> previousPrescriptionNos;

    /** 明细行。 */
    @NotEmpty(message = "申请单明细不能为空")
    @Valid
    @Schema(description = "明细行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ServiceApplyFormLineRequest> lines;

    /**
     * 合并单号字段与列表字段为编辑删旧全集（trim、去空、保序去重）。
     *
     * @return 待替换旧 AR 号；空表示新建
     */
    public List<String> resolvePreviousPrescriptionNosForReplace() {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (this.previousPrescriptionNos != null) {
            for (String raw : this.previousPrescriptionNos) {
                if (raw == null) {
                    continue;
                }
                String trimmed = raw.trim();
                if (!trimmed.isEmpty()) {
                    ordered.add(trimmed);
                }
            }
        }
        if (this.previousPrescriptionNo != null) {
            String trimmed = this.previousPrescriptionNo.trim();
            if (!trimmed.isEmpty()) {
                ordered.add(trimmed);
            }
        }
        return new ArrayList<>(ordered);
    }
}
