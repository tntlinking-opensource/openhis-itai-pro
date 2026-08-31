/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.prescriptionreview.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 创建单条处方点评命令（DM-CUS-015 §5.1.1）。
 */
@Getter
@Setter
public class CreatePrescriptionReviewCommand {

    /** 处方号。 */
    private String prescriptionNo;
    /** 点评人。 */
    private Long reviewerId;
    /** 点评时间；空则领域取当前时间。 */
    private LocalDateTime reviewedAt;
    /** 是否合理。 */
    private Boolean reasonableFlag;
    /** 原因。 */
    private String reason;
    /** 备注。 */
    private String remark;
    /** 操作人（事件载荷）。 */
    private Long operatorId;
}
