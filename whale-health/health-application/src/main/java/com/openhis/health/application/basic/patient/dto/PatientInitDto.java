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
    /** 学籍-学历层次（EducationLevel） */
    private List<OptionDto> educationLevelOptions;
    /** 学籍-学习形式（StudyMode） */
    private List<OptionDto> studyModeOptions;
    /** 学籍-在校状态（StudentStatus） */
    private List<OptionDto> studentStatusOptions;
    /** 学籍-体检结果（PhysicalExamResult） */
    private List<OptionDto> physicalExamResultOptions;

    /** 学生开关配置值（1=显示学籍信息按钮，其他=隐藏） */
    private String studentSwitchValue;
}
