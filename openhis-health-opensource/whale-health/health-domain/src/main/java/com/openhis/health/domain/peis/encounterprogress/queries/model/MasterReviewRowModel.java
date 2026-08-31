/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.encounterprogress.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 总检诊台待总检行读模型（DM-PEIS-008 §4.6.2）。
 */
@Getter
@Setter
public class MasterReviewRowModel {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者姓名。 */
    private String patientName;

    /** 体检流水号。 */
    private String busNo;

    /** 分科完成比（如 5/5）。 */
    private String deptCompleteRatio;

    /** 到检/就诊开始时间。 */
    private LocalDateTime checkInTime;

    /** 患者主键。 */
    private Long patientId;

    /** 患者年龄（岁）。 */
    private Integer patientAge;

    /** 性别编码。 */
    private String genderEnum;

    /** 团检标志。 */
    private Boolean groupFlag;
}
