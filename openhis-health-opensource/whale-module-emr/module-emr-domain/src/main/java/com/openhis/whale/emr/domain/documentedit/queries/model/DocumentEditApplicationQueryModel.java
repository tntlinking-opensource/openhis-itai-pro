/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.domain.documentedit.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 文书编辑申请列表读模型（不含双 JSON 正文，避免列表膨胀）。
 */
@Getter
@Setter
public class DocumentEditApplicationQueryModel {

    /** 申请主键。 */
    private Long id;
    /** 文书主键。 */
    private Long documentId;
    /** 就诊标识。 */
    private String encounterId;
    /** 书写科室。 */
    private Long organizationId;
    /** 门诊/住院 class。 */
    private String encounterClassCode;
    /** 状态 code。 */
    private String statusEnum;
    /** 申请原因。 */
    private String applyReason;
    /** 驳回原因。 */
    private String rejectReason;
    /** 申请时文书版本。 */
    private Integer fromVersion;
    /** 目标版本。 */
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
