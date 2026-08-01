/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.diagnosticreportindex.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 报告读侧索引（表 {@code cln_diagnostic_report_catalog}，DM-CLN-006）。
 *
 * <p>文档逻辑名 index；DDL 落点为 catalog。不存报告正文。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cln_diagnostic_report_catalog")
public class DiagnosticReportIndex extends BaseAuditedEntity {

    /** 院区/机构标识。 */
    @Column(name = "org_id")
    private Long orgId;

    /** 来源模块枚举（LIS/PACS/PEIS/…）。 */
    @Column(name = "module_enum", nullable = false)
    private String moduleEnum;

    /** 源报告主键（模块内 DR id）。 */
    @Column(name = "source_report_id", nullable = false)
    private Long sourceReportId;

    /** 就诊主键。 */
    @Column(name = "encounter_id")
    private Long encounterId;

    /** 服务申请主键。 */
    @Column(name = "service_request_id")
    private Long serviceRequestId;

    /** 患者主键。 */
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** 状态枚举。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /** 发布时间。 */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** 列表标题。 */
    @Column(name = "title")
    private String title;

    /** 分类编码。 */
    @Column(name = "category_code")
    private String categoryCode;

    /** PDF 存储键。 */
    @Column(name = "pdf_storage_key")
    private String pdfStorageKey;

    /** 外部查看 URL。 */
    @Column(name = "external_view_url")
    private String externalViewUrl;

    /** 修正来源报告标识。 */
    @Column(name = "amended_from_id")
    private Long amendedFromId;

    /** 幂等唯一键（如 PEIS:{id}）。 */
    @Column(name = "unique_key", nullable = false)
    private String uniqueKey;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}
