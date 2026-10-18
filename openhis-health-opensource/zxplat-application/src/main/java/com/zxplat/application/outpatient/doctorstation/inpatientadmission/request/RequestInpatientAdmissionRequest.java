/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.inpatientadmission.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.outpatient.doctorstation.inpatientadmission.dto.AdmissionDiagnosisSubmitDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * IF-IP-REQ-01：发起住院申请请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-住院申请-发起住院申请请求")
public class RequestInpatientAdmissionRequest {

    /** 源门诊就诊主键。 */
    @NotNull(message = "门诊就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "源门诊就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long ambEncounterId;

    /** 患者主键；传入时须与源就诊头表一致。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者主键；可选，传入须与源就诊患者一致。 */
    @Schema(description = "患者主键；可选，传入须与源就诊患者一致")
    private Long patientId;

    /** 意向住院科室。 */
    @NotNull(message = "意向住院科室不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 拟收治科室主键。 */
    @Schema(description = "拟收治科室主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long targetDepartmentId;

    /** 意向病区位置（WARD 形态）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 意向病区位置主键。 */
    @Schema(description = "意向病区位置主键")
    private Long wardId;

    /** 申请医师。 */
    @NotNull(message = "申请医师不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 申请医师主键。 */
    @Schema(description = "申请医师主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long requestDoctorId;

    /** 入院来源编码。 */
    @Schema(description = "入院来源编码")
    private String admitSourceCode;

    /** 入院方式编码。 */
    @Schema(description = "入院方式编码")
    private String inWayCode;

    /** 病情优先级编码。 */
    @Schema(description = "病情优先级编码")
    private String priorityCode;

    /** 入院诊断明细（UI 可预填门诊主诊，后台按列表为住院线落库）。 */
    @NotEmpty(message = "入院诊断不能为空")
    @Valid
    /** 入院诊断明细列表。 */
    @Schema(description = "入院诊断明细列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<AdmissionDiagnosisSubmitDto> admissionDiagnoses;

    /** 操作人。 */
    @NotNull(message = "操作人不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 操作人主键。 */
    @Schema(description = "操作人主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long operatorId;

    /**
     * 费用性质意向（{@code ChargeIntent} code：{@code INSURANCE}=医保 / {@code SELF_PAY}=自费）。
     *
     * <p>写入新建住院线，仅提示款台；不建账户、不触发医保入院。
     * 真实费用性质仍以住院登记提交的合同为准（AS-IP-FIN-001）。</p>
     */
    @Schema(description = "费用性质意向：INSURANCE=医保 / SELF_PAY=自费")
    private String chargeIntent;

    /**
     * 意向预交金总额（医生只填总额，不填支付渠道）。
     *
     * <p>>0 时对新建住院线落 {@code HOSPITAL_DEPOSIT} DRAFT；未填或 ≤0 不落草稿。
     * 开户与 settle 仅在住院登记确认时发生（AS-IP-FIN-001）。</p>
     */
    @Schema(description = "意向预交金总额；>0 落 DRAFT，医生不填支付方式")
    private BigDecimal advanceDepositAmount;
}
