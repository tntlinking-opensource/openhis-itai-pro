/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.patient.dto;

import com.openhis.whale.core.application.dto.OptionDto;
import lombok.Data;

import java.util.List;

/**
 * 患者管理初始化 DTO（患者管理菜单）：枚举与学籍相关下拉数据。
 *
 *
 */
@Data
public class PatientInitDto {

    /** 婚姻状况 */
    private List<OptionDto> maritalStatus;
    /** 职业类型 */
    private List<OptionDto> occupationType;
    /** 性别 */
    private List<OptionDto> sex;
    /** 血型 ABO */
    private List<OptionDto> bloodTypeABO;
    /** 血型 Rh */
    private List<OptionDto> bloodTypeRH;
    /** 联系人关系 */
    private List<OptionDto> familyRelationshipType;
    /** 证件类型（含身份证等） */
    private List<OptionDto> identityDocumentType;
    /** 是否类通用选项 */
    private List<OptionDto> whetherStatus;
    /** 学籍-学历层次字典（EducationLevel） */
    private List<OptionDto> educationLevelOptions;
    /** 学籍-学习形式（StudyMode） */
    private List<OptionDto> studyModeOptions;
    /** 学籍-在校状态（StudentStatus） */
    private List<OptionDto> studentStatusOptions;
    /** 学籍-体检结果（PhysicalExamResult） */
    private List<OptionDto> physicalExamResultOptions;
}
