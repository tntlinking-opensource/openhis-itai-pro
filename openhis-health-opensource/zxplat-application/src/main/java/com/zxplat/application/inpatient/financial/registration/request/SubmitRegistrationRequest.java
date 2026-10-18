/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.request;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.inpatient.financial.registration.dto.AdmissionDiagnosisSubmitDto;
import com.zxplat.application.inpatient.financial.registration.dto.PaymentDetailDto;
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

    /** 已有医生申请时的住院就诊 ID（业务字段）。 */
    @Schema(description = "已有医生申请时的住院就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 已有患者 ID。 */
    @Schema(description = "已有患者 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 身份证号。 */
    @Schema(description = "身份证号（无 patientId 时用于查重/建档）")
    private String idCard;

    /** 患者姓名。 */
    @Schema(description = "患者姓名（新建患者时必填）")
    private String patientName;

    /** 性别 code。 */
    @Schema(description = "性别 code")
    private String genderEnum;

    /** 出生日期。 */
    @Schema(description = "出生日期")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime birthDate;

    /** 联系电话。 */
    @Schema(description = "联系电话")
    private String phone;

    /** 婚姻状况编码。 */
    @Schema(description = "婚姻状况编码")
    private String maritalStatusEnum;

    /** 职业编码。 */
    @Schema(description = "职业编码")
    private String prfsEnum;

    /** 现住址详细地址。 */
    @Schema(description = "现住址详细地址")
    private String address;

    /** 现住址省。 */
    @Schema(description = "现住址省")
    private String addressProvince;

    /** 现住址市。 */
    @Schema(description = "现住址市")
    private String addressCity;

    /** 现住址区县。 */
    @Schema(description = "现住址区县")
    private String addressDistrict;

    /** 现住址街道。 */
    @Schema(description = "现住址街道")
    private String addressStreet;

    /** 国籍编码（country_code → 病案 ntly）。 */
    @Schema(description = "国籍编码")
    private String countryCode;

    /** 民族编码（nationality_code → 病案 naty）。 */
    @Schema(description = "民族编码")
    private String nationalityCode;

    /** 籍贯（省市区 code 斜杠拼接）。 */
    @Schema(description = "籍贯")
    private String nativePlace;

    /** 工作单位。 */
    @Schema(description = "工作单位")
    private String workCompany;

    /** 联系人姓名。 */
    @Schema(description = "联系人姓名")
    private String linkName;

    /** 联系人关系编码。 */
    @Schema(description = "联系人关系编码")
    private Integer linkRelationCode;

    /** 联系人电话。 */
    @Schema(description = "联系人电话")
    private String linkTelcom;

    /** 同身份证是否更新主档。 */
    @Schema(description = "同身份证是否更新主档")
    private Boolean updatePatientFromIdCard;

    /** 住院科室 ID。 */
    @Schema(description = "住院科室 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;

    /** 入院病区位置 ID。 */
    @Schema(description = "入院病区位置 ID（WARD）", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long wardId;

    /** 入院来源 code。 */
    @Schema(description = "入院来源 code")
    private String admitSourceCode;

    /** 入院途径 code。 */
    @Schema(description = "入院途径 code")
    private String inWayCode;

    /** 入院时间。 */
    @Schema(description = "入院时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime startTime;

    /** 病情优先级 code。 */
    @Schema(description = "病情优先级 code")
    private String priorityEnum;

    /** 费用性质。 */
    @Schema(description = "费用性质（合同）ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 预交金金额；大于 0 时收款。 */
    @Schema(description = "预交金金额；大于 0 时收款")
    private BigDecimal advanceDepositAmount;

    /** 预交金支付明细。 */
    @Schema(description = "预交金支付明细（有预交金时必填）")
    private List<PaymentDetailDto> paymentDetails;

    /** 入院诊断列表。 */
    @Schema(description = "入院诊断列表")
    private List<AdmissionDiagnosisSubmitDto> admissionDiagnoses;

    /** 操作人/登记员 ID。 */
    @Schema(description = "操作人/登记员 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    /** 就诊凭证类型。 */
    @Schema(description = "就诊凭证类型（医保读卡）")
    private String ybMediumType;

    /** 就诊凭证编号。 */
    @Schema(description = "就诊凭证编号（医保读卡）")
    private String ybCardInfo;

    /** 人员编号。 */
    @Schema(description = "人员编号（1101）")
    private String psnNo;

    /** 参保地医保区划。 */
    @Schema(description = "参保地医保区划")
    private String insuplcAdmdvs;

    /** 险种类型。 */
    @Schema(description = "险种类型")
    private String insutype;

    /** 医疗类别。 */
    @Schema(description = "医疗类别，默认 21 普通住院")
    private String medType;

    /** 主治医生医务人员 ID（医保登记 2401 须传 atddr_no）。 */
    @Schema(description = "主治医生医务人员 ID（医保登记必填）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long attendingDoctorId;
}
