/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.domain.document.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 文书版本快照。表 emr_document_version。
 */
@Getter
@Setter
@Entity
@Table(name = "emr_document_version")
public class DocumentVersion extends BaseAuditedEntity {

    /**
     * 文书 ID
     */
    @Column(name = "document_id", nullable = false)
    private Long documentId;

    /**
     * 版本号，同一文书内递增
     */
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    /**
     * 版本内容 JSON
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
}
