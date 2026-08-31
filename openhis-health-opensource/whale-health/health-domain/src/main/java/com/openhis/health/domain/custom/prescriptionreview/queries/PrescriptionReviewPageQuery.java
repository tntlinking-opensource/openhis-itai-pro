/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.prescriptionreview.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 处方点评分页查询入参（DM-CUS-015 §5.2）。
 */
@Getter
@Setter
public class PrescriptionReviewPageQuery {

    /** 点评状态 code；{@code PENDING} 时读侧返回空页。 */
    private String statusEnum;
    /** 点评时间起（含）。 */
    private LocalDateTime reviewedAtFrom;
    /** 点评时间止（含）。 */
    private LocalDateTime reviewedAtTo;
    /** 是否合理。 */
    private Boolean reasonableFlag;
    /** 点评人。 */
    private Long reviewerId;
    /** 处方号关键字（模糊）。 */
    private String prescriptionNoKeyword;
    /** 业务号关键字（模糊）。 */
    private String busNoKeyword;
}
