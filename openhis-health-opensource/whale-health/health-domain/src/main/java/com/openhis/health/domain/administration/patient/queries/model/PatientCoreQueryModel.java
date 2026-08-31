/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 患者核心查询模型。
 */
@Getter
@Setter
public class PatientCoreQueryModel {

    /** 患者标识。 */
    private Long id;
    /** 院内编码。 */
    private String busNo;
    /** 姓名。 */
    private String name;
    /** 姓名扩展 JSON。 */
    private String nameJson;
    /** 性别编码。 */
    private String genderEnum;
    /** 出生日期。 */
    private LocalDate birthDate;
    /** 身份证号。 */
    private String idCard;
    /** 婚姻状态编码。 */
    private String maritalStatusEnum;
    /** 职业编码。 */
    private String prfsEnum;
    /** 联系电话。 */
    private String phone;
    /** 地址全文。 */
    private String address;
    /** 地址省份。 */
    private String addressProvince;
    /** 地址城市。 */
    private String addressCity;
    /** 地址区县。 */
    private String addressDistrict;
    /** 地址街道。 */
    private String addressStreet;
    /** 地址扩展 JSON。 */
    private String addressExtendJson;
    /** 民族编码。 */
    private String nationalityCode;
    /** 学历层次字典编码（EducationLevel）。 */
    private String educationLevelCode;
    /** 拼音码。 */
    private String pyStr;
    /** 五笔码。 */
    private String wbStr;
    /** ABO 血型编码。 */
    private Integer bloodTypeAbo;
    /** RH 血型编码。 */
    private Integer bloodTypeRh;
    /** 工作单位。 */
    private String workCompany;
    /** 籍贯。 */
    private String nativePlace;
    /** 国家/地区编码。 */
    private String countryCode;
    /** 联系人姓名。 */
    private String linkName;
    /** 联系人关系编码。 */
    private Integer linkRelationCode;
    /** 联系人电话。 */
    private String linkTelcom;
    /** 联系人扩展 JSON。 */
    private String linkJsons;
    /** 状态编码。 */
    private String statusEnum;
    /** 启用标记。 */
    private Boolean activeFlag;
    /** 临时标记。 */
    private Boolean tempFlag;
    /** 建档机构。 */
    private Long organizationId;
    /** 死亡时间。 */
    private LocalDateTime deceasedDate;
    /** 死亡原因。 */
    private String deceasedReason;
    /** 合并目标患者标识。 */
    private Long mergedTargetPatientId;
    /** 合并时间。 */
    private LocalDateTime mergedAt;
}
