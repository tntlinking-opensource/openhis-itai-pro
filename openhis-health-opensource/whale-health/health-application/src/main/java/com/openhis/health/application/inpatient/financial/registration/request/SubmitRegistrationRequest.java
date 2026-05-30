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

package com.openhis.health.application.inpatient.financial.registration.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.financial.registration.dto.AdmissionDiagnosisSubmitDto;
import com.openhis.health.application.inpatient.financial.registration.dto.PaymentDetailDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 提交住院登记请求（IF-IP-REG-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "提交住院登记请求")
public class SubmitRegistrationRequest {

    @Schema(description = "已有医生申请时的住院就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    @Schema(description = "已有患者 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    @Schema(description = "身份证号（无 patientId 时用于查重/建档）")
    private String idCard;

    @Schema(description = "患者姓名（新建患者时必填）")
    private String patientName;

    @Schema(description = "性别 code")
    private String genderEnum;

    @Schema(description = "出生日期")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime birthDate;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "同身份证是否更新主档")
    private Boolean updatePatientFromIdCard;

    @Schema(description = "住院科室 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;

    @Schema(description = "入院病区位置 ID（WARD）", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long wardId;

    @Schema(description = "入院来源 code")
    private String admitSourceCode;

    @Schema(description = "入院途径 code")
    private String inWayCode;

    @Schema(description = "入院时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Schema(description = "病情优先级 code")
    private String priorityEnum;

    @Schema(description = "费用性质（合同）ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    @Schema(description = "预交金金额；大于 0 时收款")
    private BigDecimal advanceDepositAmount;

    @Schema(description = "预交金支付明细（有预交金时必填）")
    private List<PaymentDetailDto> paymentDetails;

    @Schema(description = "入院诊断列表")
    private List<AdmissionDiagnosisSubmitDto> admissionDiagnoses;

    @Schema(description = "操作人/登记员 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;
}
