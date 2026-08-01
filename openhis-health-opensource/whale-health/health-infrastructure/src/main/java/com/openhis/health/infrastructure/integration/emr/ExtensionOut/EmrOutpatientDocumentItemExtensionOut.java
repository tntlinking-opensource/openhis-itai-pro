/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.emr.ExtensionOut;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 门诊文书分页行（跨患者检索摘要，不含正文）。
 */
@Getter
@Setter
public class EmrOutpatientDocumentItemExtensionOut {

    /** 文书主键。 */
    private Long documentId;

    /** 患者主键（字符串，与 emr_document.patient_id 一致）。 */
    private String patientId;

    /** 就诊主键（字符串，与 emr_document.encounter_id 一致）。 */
    private String encounterId;

    /** 文书标题。 */
    private String title;

    /** 文书类型 code。 */
    private String documentTypeCode;

    /** 文书类型展示名。 */
    private String documentTypeName;

    /** 文书状态 code。 */
    private String statusCode;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;

    /** 创建人用户主键。 */
    private Long createdBy;

    /** 更新人用户主键。 */
    private Long updatedBy;

    /** 是否允许打印/导出 PDF。 */
    private boolean printable;
}
