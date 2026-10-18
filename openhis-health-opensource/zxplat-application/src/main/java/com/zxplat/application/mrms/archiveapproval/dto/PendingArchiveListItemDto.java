/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.archiveapproval.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 待归档审批列表行。
 */
@Getter
@Setter
public class PendingArchiveListItemDto {

    /** 业务类别 code。 */
    private String bizCategory;

    /** 业务主键。 */
    private Long bizId;

    /** 展示标题（文书标题或病案号/姓名）。 */
    private String title;

    /** 患者姓名（首页有；文书可空）。 */
    private String patientName;

    /** 提交人姓名（由提交人用户主键解析医务人员姓名）。 */
    private String submitterName;

    /** 就诊标识（字符串或数字转串）。 */
    private String encounterId;

    /** 状态 code。 */
    private String statusCode;

    /** 提交时间。 */
    private LocalDateTime submittedAt;

    /** 书写科室（文书）。 */
    private Long organizationId;

    /** 版本号。 */
    private Integer versionNo;
}
