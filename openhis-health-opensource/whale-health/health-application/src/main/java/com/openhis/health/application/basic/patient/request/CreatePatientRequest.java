/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.patient.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建患者请求（患者管理菜单入参）。
 *
 *
 */
@Data
public class CreatePatientRequest {

    /** 患者姓名。 */
    @NotBlank(message = "患者姓名不能为空")
    private String name;
    /** 国籍编码。 */
    private String nationalityCode;
    /** 性别枚举编码（AdministrativeGender#getCode()）。 */
    private String genderEnum;
    /** 是否启用患者主档。 */
    private Boolean activeFlag;
    /** 证件号（主证件）。 */
    private String idCard;
    /** 前端估算年龄（后端可能重新计算，仅供参考）。 */
    private Integer age;
    /** 出生日期时刻（接口传入 LocalDateTime，持久化为日期部分）。 */
    private LocalDateTime birthDate;
    /** 联系电话。 */
    private String phone;
    /** 职业枚举编码。 */
    private String prfsEnum;
    /** 工作单位。 */
    private String workCompany;
    /** 联系人姓名。 */
    private String linkName;
    /** 联系人关系字典码。 */
    private Integer linkRelationCode;
    /** 联系人电话。 */
    private String linkTelcom;
    /** 合并地址文本。 */
    private String address;
    /** 省。 */
    private String addressProvince;
    /** 市。 */
    private String addressCity;
    /** 区县。 */
    private String addressDistrict;
    /** 街道。 */
    private String addressStreet;
    /** ABO 血型编码。 */
    private Integer bloodAbo;
    /** Rh 血型编码。 */
    private Integer bloodRh;
    /** 婚姻状况枚举编码。 */
    private String maritalStatusEnum;
    /** 死亡时间（若有）。 */
    private LocalDateTime deceasedDate;
    /** 其它证件/标识行列表。 */
    private List<CreatePatientIdentifierRequest> patientIdInfoList;

    @Data
    public static class CreatePatientIdentifierRequest {
        /** 标识用途：1常用 2正式 3临时 4次要 5旧，见 IdentifierState */
        private String useEnum;
        /** 证件类型编码（字典）。 */
        private String typeCode;
        /** 证件号码。 */
        private String identifierNo;
        /** 有效期开始，格式 yyyy-MM-dd HH:mm:ss。 */
        private String startTime;
        /** 有效期结束，格式 yyyy-MM-dd HH:mm:ss。 */
        private String endTime;
    }
}
