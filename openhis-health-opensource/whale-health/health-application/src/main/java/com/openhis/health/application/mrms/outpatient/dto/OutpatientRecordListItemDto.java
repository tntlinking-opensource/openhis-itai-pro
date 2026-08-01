/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.outpatient.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 门诊病历查询列表行 DTO。
 */
@Getter
@Setter
public class OutpatientRecordListItemDto {

    /** 文书主键。 */
    private Long documentId;

    /** 患者主键。 */
    private Long patientId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者姓名。 */
    private String patientName;

    /** 门诊号（就诊 busNo）。 */
    private String outpatientNo;

    /** 就诊科室名称。 */
    private String deptName;

    /** 就诊开始时间。 */
    private LocalDateTime encounterTime;

    /** 文书标题。 */
    private String title;

    /** 文书类型 code。 */
    private String documentTypeCode;

    /** 文书类型展示名。 */
    private String documentTypeName;

    /** 文书状态 code。 */
    private String statusCode;

    /** 更新时间。 */
    private LocalDateTime updatedAt;

    /** 是否允许打印。 */
    private boolean printable;
}
