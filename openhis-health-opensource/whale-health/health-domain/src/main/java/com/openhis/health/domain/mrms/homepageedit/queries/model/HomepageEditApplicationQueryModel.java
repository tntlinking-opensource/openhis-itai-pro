/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.homepageedit.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 首页编辑申请列表行（不含双 JSON）。
 */
@Getter
@Setter
public class HomepageEditApplicationQueryModel {

    /** 申请主键。 */
    private Long id;
    /** 病案主档。 */
    private Long caseRecordId;
    /** 就诊。 */
    private Long encounterId;
    /** 状态。 */
    private String statusEnum;
    /** 申请原因。 */
    private String applyReason;
    /** 驳回原因。 */
    private String rejectReason;
    /** 旧版本。 */
    private Integer fromVersion;
    /** 新版本。 */
    private Integer toVersion;
    /** 申请人。 */
    private Long applicantId;
    /** 申请时间。 */
    private LocalDateTime appliedAt;
    /** 交审时间。 */
    private LocalDateTime submittedAt;
    /** 审核人。 */
    private Long reviewerId;
    /** 审核时间。 */
    private LocalDateTime reviewedAt;
}
