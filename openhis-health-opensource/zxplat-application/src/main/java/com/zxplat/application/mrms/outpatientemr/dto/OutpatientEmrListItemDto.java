/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.outpatientemr.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 门诊病历查询列表行 DTO。
 *
 * <p>Long 主键出参统一字符串，避免前端 Number 精度丢失导致接诊医生比对误判。</p>
 */
@Getter
@Setter
public class OutpatientEmrListItemDto {

    /** 文书主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;

    /** 患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者姓名。 */
    private String patientName;

    /** 门诊号（就诊 busNo）。 */
    private String outpatientNo;

    /** 就诊科室名称。 */
    private String deptName;

    /** 接诊医生姓名（活动 ADMITTER）。 */
    private String doctorName;

    /**
     * 接诊医生医务人员主键（活动 ADMITTER）。
     * <p>前端与登录 {@code practitionerId} 比对：相同可编辑门诊病历，否则只读。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long admitterPractitionerId;

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

    /**
     * 病例是否已归档（文书 PENDING_ARCHIVE/ARCHIVED）；归档后临床只读。
     */
    private boolean clinicalArchived;
}
