/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 医保文件通知文件头（{@code yb_catalog_notice_doc}）。
 * <p>聚合根：承载标准落地包 URI、解析/应用状态与行数摘要。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "yb_catalog_notice_doc")
public class YbCatalogNoticeDoc extends BaseAuditedEntity {

    /** 契约主键；可空表示租户级通用件。 */
    @Column(name = "contract_id")
    private Long contractId;

    /** 文件种类：{@code CATALOG_DELTA} / {@code PAYMENT_MAP}。 */
    @Column(name = "doc_kind", nullable = false, length = 32)
    private String docKind;

    /** 品类：DRUG/SERVICE/SERVICE_F/DEVICE/FORMULA/HERBAL/PREP/OTHER。 */
    @Column(name = "category", nullable = false, length = 32)
    private String category;

    /** 展示标题；默认可同 source_filename。 */
    @Column(name = "title", length = 256)
    private String title;

    /** 局发原文件名（审计展示用）。 */
    @Column(name = "source_filename", length = 512)
    private String sourceFilename;

    /** 标准落地包内容 SHA-256，租户内去重键。 */
    @Column(name = "source_file_hash", nullable = false, length = 64)
    private String sourceFileHash;

    /** 标准落地包存储位置（必留）。 */
    @Column(name = "canonical_pack_uri", nullable = false, length = 1024)
    private String canonicalPackUri;

    /** 局发原件 URI；一期可空。 */
    @Column(name = "source_file_uri", length = 1024)
    private String sourceFileUri;

    /** 通知日期。 */
    @Column(name = "notice_date")
    private LocalDate noticeDate;

    /** 默认生效日；可被行级日期覆盖。 */
    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    /** 解析状态：PENDING/PARSED/PARSE_FAILED/PARTIAL。 */
    @Column(name = "parse_status", nullable = false, length = 32)
    private String parseStatus;

    /** 应用状态：NOT_APPLIED/APPLYING/PARTIAL/APPLIED/REVOKED。 */
    @Column(name = "apply_status", nullable = false, length = 32)
    private String applyStatus;

    /** 各 sheet 行数摘要 JSON。 */
    @Column(name = "sheet_summary_json", columnDefinition = "TEXT")
    private String sheetSummaryJson;

    /** 解析失败信息。 */
    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    /** 导入人。 */
    @Column(name = "imported_by")
    private Long importedBy;

    /** 导入时间。 */
    @Column(name = "imported_at")
    private LocalDateTime importedAt;

    /** 应用人。 */
    @Column(name = "applied_by")
    private Long appliedBy;

    /** 应用时间。 */
    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    /** 明细行数。 */
    @Column(name = "line_count", nullable = false)
    private Integer lineCount = 0;
}
