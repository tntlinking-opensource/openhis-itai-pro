/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.emr.ExtensionOut;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 文书编辑申请出参（详情可含双 JSON；列表行可不填）。
 */
@Getter
@Setter
public class EmrDocumentEditApplicationExtensionOut {

    /** 申请主键。 */
    private Long applicationId;

    /** 文书主键。 */
    private Long documentId;

    /** 就诊标识。 */
    private String encounterId;

    /** 书写科室。 */
    private Long organizationId;

    /** AMB/IMP。 */
    private String encounterClassCode;

    /** 状态。 */
    private String statusEnum;

    /** 申请原因。 */
    private String applyReason;

    /** 驳回原因。 */
    private String rejectReason;

    /** from 版本。 */
    private Integer fromVersion;

    /** to 版本。 */
    private Integer toVersion;

    /** 旧 JSON（详情）。 */
    private String oldContentJson;

    /** 新 JSON（详情/工作稿）。 */
    private String newContentJson;

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
