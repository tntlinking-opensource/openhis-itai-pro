/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.domain.document.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.whale.core.domain.IMultiTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 电子病历文书实体。表 emr_document。
 */
@Getter
@Setter
@Entity
@Table(name = "emr_document")
public class Document extends BaseAuditedEntity implements IMultiTenant {

    /**
     * 患者 ID（HIS 患者标识），varchar(64)
     */
    @Size(max = 64)
    @Column(name = "patient_id", length = 64)
    private String patientId;

    /**
     * 就诊 ID（HIS 就诊标识），varchar(64)
     */
    @Size(max = 64)
    @Column(name = "encounter_id", length = 64)
    private String encounterId;

    /**
     * 文书类型编码，如入院记录、病程记录等
     */
    @Size(max = 32)
    @Column(name = "document_type_code", length = 32)
    private String documentTypeCode;

    /**
     * 文档标题（可选），例如“入院记录-2025-03-15”
     */
    @Size(max = 256)
    @Column(name = "title", length = 256)
    private String title;

    /**
     * 文档正文内容，Tiptap/ProseMirror JSON。
     * PostgreSQL 建议使用 JSONB 或 TEXT 存储。
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /**
     * 文档状态：DRAFT / PENDING_ARCHIVE / ARCHIVED（DM-EMR-001）。
     */
    @Size(max = 32)
    @Column(name = "status", length = 32)
    private String status;

    /**
     * 书写时科室（创建写入；转科不改写）。
     */
    @Column(name = "organization_id")
    private Long organizationId;

    /**
     * 业务版本号：创建=1；首次归档不升；编辑审核通过 +1。
     */
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    /** 提交归档人。 */
    @Column(name = "archive_submitted_by")
    private Long archiveSubmittedBy;

    /** 提交归档时间。 */
    @Column(name = "archive_submitted_at")
    private java.time.LocalDateTime archiveSubmittedAt;

    /** 归档审批同意人。 */
    @Column(name = "archive_approved_by")
    private Long archiveApprovedBy;

    /** 归档审批同意时间。 */
    @Column(name = "archive_approved_at")
    private java.time.LocalDateTime archiveApprovedAt;

    /** 归档驳回原因。 */
    @Size(max = 500)
    @Column(name = "archive_reject_reason", length = 500)
    private String archiveRejectReason;

    /**
     * 创建文书时使用的模板 ID（如首次病程 PN_FIRST 溯源与唯一性校验）
     */
    @Column(name = "source_template_id")
    private Long sourceTemplateId;
}
