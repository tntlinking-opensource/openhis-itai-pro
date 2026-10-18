/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.emr.ExtensionOut;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 文书归档审批详情出参（含正文）。
 */
@Getter
@Setter
public class EmrDocumentArchiveDetailExtensionOut {

    /** 文书主键；未找到时为 null。 */
    private Long documentId;

    /** 患者标识。 */
    private String patientId;

    /** 就诊标识。 */
    private String encounterId;

    /** 标题。 */
    private String title;

    /** 类型。 */
    private String documentTypeCode;

    /** 状态。 */
    private String statusCode;

    /** 书写科室。 */
    private Long organizationId;

    /** 版本。 */
    private Integer versionNo;

    /** 正文 JSON。 */
    private String content;

    /** 提交时间。 */
    private LocalDateTime archiveSubmittedAt;

    /** 驳回原因（若曾驳回）。 */
    private String archiveRejectReason;
}
