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

package com.openhis.health.domain.administration.patient.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 患者学生记录查询模型。
 */
@Getter
@Setter
public class PatientStudentQueryModel {

    /** 学生记录标识。 */
    private Long id;
    /** 学生姓名。 */
    private String name;
    /** 学号。 */
    private String studentNo;
    /** 身份证号。 */
    private String idCard;
    /** 联系电话。 */
    private String phone;
    /** 学院。 */
    private String college;
    /** 专业。 */
    private String major;
    /** 学历层次。 */
    private Integer eduLevel;
    /** 入校时间。 */
    private LocalDateTime enrollTime;
    /** 离校时间。 */
    private LocalDateTime leaveTime;
    /** 年级。 */
    private String grade;
    /** 学习形式。 */
    private Integer studyType;
    /** 在校状态。 */
    private Integer schoolStatus;
    /** 体检结果。 */
    private Integer physicalExamResult;
    /** 辅导员。 */
    private String counselor;
    /** 辅导员电话。 */
    private String counselorPhone;
}
