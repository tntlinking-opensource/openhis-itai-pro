/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.prescriptionreview.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 处方点评读模型（DM-CUS-015 §4.6.1）。
 */
@Getter
@Setter
public class PrescriptionReviewQueryModel {

    /** 点评主键。 */
    private Long id;
    /** 点评业务号（PRV 日序）。 */
    private String busNo;
    /** 处方号。 */
    private String prescriptionNo;
    /** 点评人。 */
    private Long reviewerId;
    /** 点评时间。 */
    private LocalDateTime reviewedAt;
    /** 是否合理。 */
    private Boolean reasonableFlag;
    /** 点评状态 code。 */
    private String statusEnum;
    /** 原因。 */
    private String reason;
    /** 备注。 */
    private String remark;
}
