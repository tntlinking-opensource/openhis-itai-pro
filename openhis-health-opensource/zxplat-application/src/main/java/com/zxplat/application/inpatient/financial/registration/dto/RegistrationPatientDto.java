/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.dto;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 登记弹窗患者主档 DTO。
 */
@Data
@Accessors(chain = true)
@Schema(description = "登记患者主档")
public class RegistrationPatientDto {

    /** 患者 ID（业务字段）。 */
    @Schema(description = "患者 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 姓名。 */
    @Schema(description = "姓名")
    private String name;

    /** 身份证号。 */
    @Schema(description = "身份证号")
    private String idCard;

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

    /** 现住址全文（详细地址）。 */
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

    /** 国籍编码（adm_patient.country_code → 病案 ntly）。 */
    @Schema(description = "国籍编码")
    private String countryCode;

    /** 民族编码（adm_patient.nationality_code → 病案 naty）。 */
    @Schema(description = "民族编码")
    private String nationalityCode;

    /** 籍贯（省市区 code 斜杠拼接，对齐病案 napl_codes）。 */
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
}
