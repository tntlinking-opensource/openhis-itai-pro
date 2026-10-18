/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.document.repository;

import com.zxplat.plugin.emr.domain.document.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 文书版本快照仓储。表 emr_document_version；单文书版本数随保存频率增长。
 */
@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    /** 某文书全部版本，版本号倒序（取最新号用）。 */
    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(Long documentId);

    /** 按文书 id + 版本序号定位快照。 */
    Optional<DocumentVersion> findByDocumentIdAndVersionNumber(Long documentId, Integer versionNumber);

    /** 按文书 id + 版本行 id 定位快照（回滚/预览）。 */
    Optional<DocumentVersion> findByDocumentIdAndId(Long documentId, Long id);
}
