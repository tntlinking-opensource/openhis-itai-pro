/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.masterreview.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 待总检列表行 DTO。
 */
@Data
public class MasterPendingRowDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者姓名。 */
    private String patientName;

    /** 体检流水号。 */
    private String busNo;

    /** 分科完成比。 */
    private String deptCompleteRatio;

    /** 到检时间。 */
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
