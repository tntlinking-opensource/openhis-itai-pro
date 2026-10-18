/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.patient.dto;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import lombok.Data;

/**
 * 患者信息 DTO（患者管理菜单出参）。
 *
 *
 */
@Data
public class PatientDto {

    /** 患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /** 姓名。 */
    private String name;
    /** 病历号/业务号。 */
    private String busNo;
    /** 性别枚举编码（AdministrativeGender#getCode()）。 */
    private String genderEnum;
    /** 性别展示文案。 */
    private String genderEnumText;
    /** 身份证号或其它主证件号（依建档口径）。 */
    private String idCard;
    /** 出生日期（仅日期，不含时分秒）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    private LocalDate birthDate;
    /** 联系电话。 */
    private String phone;
    /** 职业枚举编码。 */
    private String prfsEnum;
    /** 职业展示文案。 */
    private String prfsEnumText;
    /** 工作单位。 */
    private String workCompany;
    /** 联系人姓名。 */
    private String linkName;
    /** 联系人关系字典码。 */
    private Integer linkRelationCode;
    /** 联系人关系展示文案。 */
    private String linkRelationCodeText;
    /** 联系人电话。 */
    private String linkTelcom;
    /** 地址合并文本。 */
    private String address;
    /** 省。 */
    private String addressProvince;
    /** 市。 */
    private String addressCity;
    /** 区县。 */
    private String addressDistrict;
    /** 街道/详细地址。 */
    private String addressStreet;
    /** 民族编码（nationality_code → 病案 naty）。 */
    private String nationalityCode;
    /** 国籍编码（country_code → 病案 ntly）。 */
    private String countryCode;
    /** 籍贯（可为省市区 code 斜杠串）。 */
    private String nativePlace;
    /** 学历层次字典编码（EducationLevel）。 */
    private String educationLevelCode;
    /** ABO 血型编码。 */
    private Integer bloodAbo;
    /** ABO 展示文案。 */
    private String bloodAboText;
    /** Rh 血型编码。 */
    private Integer bloodRh;
    /** Rh 展示文案。 */
    private String bloodRhText;
    /** 婚姻状况枚举编码。 */
    private String maritalStatusEnum;
    /** 婚姻状况展示文案。 */
    private String maritalStatusEnumText;
    /** 死亡时间（若有）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime deceasedDate;
    /** 拼音检索码。 */
    private String pyStr;
    /** 五笔检索码。 */
    private String wbStr;
    /** 是否启用（读模型/主档启用标识）。 */
    private Boolean activeFlag;
    /** 启用状态中文标签。 */
    private String activeFlagText;
    /** 粗略年龄（列表展示用，非周岁法务口径）。 */
    private Integer age;
    /** 创建时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime createTime;
    /** 患者其他证件/标识列表（身份证以外），编辑回显使用。 */
    private List<PatientIdentifierItemDto> patientIdInfoList;
}
