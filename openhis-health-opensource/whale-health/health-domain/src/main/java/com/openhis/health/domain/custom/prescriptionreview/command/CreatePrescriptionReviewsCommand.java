/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.prescriptionreview.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量创建处方点评命令（DM-CUS-015 §5.1.2）。
 */
@Getter
@Setter
public class CreatePrescriptionReviewsCommand {

    /** 待点评处方列表。 */
    private List<CreatePrescriptionReviewItemCommand> items;
    /** 共用是否合理。 */
    private Boolean reasonableFlag;
    /** 共用原因。 */
    private String reason;
    /** 共用备注。 */
    private String remark;
    /** 共用点评人。 */
    private Long reviewerId;
    /** 共用点评时间。 */
    private LocalDateTime reviewedAt;
    /** 操作人。 */
    private Long operatorId;
}
