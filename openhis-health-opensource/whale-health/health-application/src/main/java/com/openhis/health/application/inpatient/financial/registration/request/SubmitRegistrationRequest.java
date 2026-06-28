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

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
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
/** 提交住院登记请求。 */
@Schema(description = "提交住院登记请求")
public class SubmitRegistrationRequest {

    @Schema(description = "已有医生申请时的住院就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 已有医生申请时的住院就诊 ID（业务字段）。 */
    private Long encounterId;

    /** 已有患者 ID。 */
    @Schema(description = "已有患者 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 已有患者 ID（业务字段）。 */
    private Long patientId;

    /** 身份证号。 */
    @Schema(description = "身份证号（无 patientId 时用于查重/建档）")
    /** 身份证号（无 patientId 时用于查重/建档）（业务字段）。 */
    private String idCard;

    /** 患者姓名。 */
    @Schema(description = "患者姓名（新建患者时必填）")
    /** 患者姓名（新建患者时必填）（业务字段）。 */
    private String patientName;

    /** 性别 code。 */
    @Schema(description = "性别 code")
    /** 性别 code（业务字段）。 */
    private String genderEnum;

    /** 出生日期。 */
    @Schema(description = "出生日期")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 出生日期（业务字段）。 */
    private LocalDateTime birthDate;

    /** 联系电话。 */
    @Schema(description = "联系电话")
    /** 联系电话（业务字段）。 */
    private String phone;

    /** 同身份证是否更新主档。 */
    @Schema(description = "同身份证是否更新主档")
    /** 同身份证是否更新主档（业务字段）。 */
    private Boolean updatePatientFromIdCard;

    /** 住院科室 ID。 */
    @Schema(description = "住院科室 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 住院科室 ID（业务字段）。 */
    private Long organizationId;

    /** 入院病区位置 ID。 */
    @Schema(description = "入院病区位置 ID（WARD）", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 入院病区位置 ID（WARD）（业务字段）。 */
    private Long wardId;

    /** 入院来源 code。 */
    @Schema(description = "入院来源 code")
    /** 入院来源 code（业务字段）。 */
    private String admitSourceCode;

    /** 入院途径 code。 */
    @Schema(description = "入院途径 code")
    /** 入院途径 code（业务字段）。 */
    private String inWayCode;

    /** 入院时间。 */
    @Schema(description = "入院时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 入院时间（业务字段）。 */
    private LocalDateTime startTime;

    /** 病情优先级 code。 */
    @Schema(description = "病情优先级 code")
    /** 病情优先级 code（业务字段）。 */
    private String priorityEnum;

    /** 费用性质。 */
    @Schema(description = "费用性质（合同）ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 费用性质（合同）ID（业务字段）。 */
    private Long contractId;

    /** 预交金金额；大于 0 时收款。 */
    @Schema(description = "预交金金额；大于 0 时收款")
    /** 预交金金额；大于 0 时收款（业务字段）。 */
    private BigDecimal advanceDepositAmount;

    /** 预交金支付明细。 */
    @Schema(description = "预交金支付明细（有预交金时必填）")
    /** 预交金支付明细（有预交金时必填）（业务字段）。 */
    private List<PaymentDetailDto> paymentDetails;

    /** 入院诊断列表。 */
    @Schema(description = "入院诊断列表")
    /** 入院诊断列表（业务字段）。 */
    private List<AdmissionDiagnosisSubmitDto> admissionDiagnoses;

    /** 操作人/登记员 ID。 */
    @Schema(description = "操作人/登记员 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 操作人/登记员 ID（业务字段）。 */
    private Long operatorId;

    /** 就诊凭证类型。 */
    @Schema(description = "就诊凭证类型（医保读卡）")
    /** 就诊凭证类型（医保读卡）（业务字段）。 */
    private String ybMediumType;

    /** 就诊凭证编号。 */
    @Schema(description = "就诊凭证编号（医保读卡）")
    /** 就诊凭证编号（医保读卡）（业务字段）。 */
    private String ybCardInfo;

    /** 人员编号。 */
    @Schema(description = "人员编号（1101）")
    /** 人员编号（1101）（业务字段）。 */
    private String psnNo;

    /** 参保地医保区划。 */
    @Schema(description = "参保地医保区划")
    /** 参保地医保区划（业务字段）。 */
    private String insuplcAdmdvs;

    /** 险种类型。 */
    @Schema(description = "险种类型")
    /** 险种类型（业务字段）。 */
    private String insutype;

    /** 医疗类别。 */
    @Schema(description = "医疗类别，默认 21 普通住院")
    /** 医疗类别，默认 21 普通住院（业务字段）。 */
    private String medType;

    /** 主治医生医务人员 ID（医保登记 2401 须传 atddr_no）。 */
    @Schema(description = "主治医生医务人员 ID（医保登记必填）")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 主治医生医务人员 ID（业务字段）。 */
    private Long attendingDoctorId;
}
