/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.extension.campus;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * PEIS 侧读 Campus 学生/教职工身份摘要（SPI 视图，避免 health-application 直依赖 campus 模块）。
 */
@Getter
@Setter
public class PeisCampusIdentityView {

    /** STUDENT / STAFF */
    private String subjectType;
    private Long patientId;
    private Long subjectId;
    private String name;
    private String studentNo;
    private String employeeNo;
    private String schoolStatusEnum;
    private Integer schoolYear;
    private LocalDateTime enrollTime;
    private LocalDateTime leaveTime;
    private String jobStatusEnum;
}
