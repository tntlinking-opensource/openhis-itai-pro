/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.application.capture.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 待采集列表行。
 */
@Getter
@Setter
public class PendingCaptureRowDto {

    /** 工单主键。 */
    private Long workItemId;

    /** 检查号。 */
    private String accessionNumber;

    /** 患者主键。 */
    private Long patientId;

    /** 科室。 */
    private Long organizationId;

    /** 模态码。 */
    private String modalityCode;

    /** 部位码。 */
    private String bodySiteCode;

    /** 完成时间。 */
    private LocalDateTime performedTime;

    /** 是否已有 Study。 */
    private Boolean hasStudy;

    /** 关键图数量。 */
    private Long keyImageCount;
}
