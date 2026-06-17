/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.whale.emr.domain.repository;

import com.openhis.whale.emr.domain.entity.DocumentVersion;
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
