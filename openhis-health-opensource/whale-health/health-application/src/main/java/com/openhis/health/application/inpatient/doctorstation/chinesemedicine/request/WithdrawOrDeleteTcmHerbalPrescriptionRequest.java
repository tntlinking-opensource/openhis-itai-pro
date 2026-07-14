/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-TCM-38：签退或删除中药处方请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "签退或删除中药处方请求")
public class WithdrawOrDeleteTcmHerbalPrescriptionRequest {

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

    /** 动作 WITHDRAW/DELETE。 */
    @NotBlank(message = "action 不能为空")
    @Schema(description = "动作 WITHDRAW/DELETE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;

    /** 范围 PRESCRIPTION/LINE。 */
    @NotBlank(message = "scope 不能为空")
    @Schema(description = "范围 PRESCRIPTION/LINE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String scope;

    /** 目标药品申请 id 列表。 */
    @NotEmpty(message = "medicationRequestIds 不能为空")
    @Schema(description = "目标药品申请 id 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> medicationRequestIds;

    /** 整方时携带的前端方标识。 */
    @Schema(description = "整方时携带的前端方标识")
    private String prescriptionGroupKey;

    /** 整方已签发时携带的处方号。 */
    @Schema(description = "整方已签发时携带的处方号")
    private String prescriptionNo;
}
