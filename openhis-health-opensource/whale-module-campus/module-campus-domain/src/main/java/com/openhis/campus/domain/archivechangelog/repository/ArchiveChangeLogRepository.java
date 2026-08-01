/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.archivechangelog.repository;

import com.openhis.campus.domain.archivechangelog.entity.ArchiveChangeLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArchiveChangeLogRepository extends JpaRepository<ArchiveChangeLog, Long> {

    Page<ArchiveChangeLog> findByTenantIdAndSubjectTypeEnumAndSubjectIdOrderByChangeTimeDesc(
        Long tenantId, String subjectTypeEnum, Long subjectId, Pageable pageable);

    List<ArchiveChangeLog> findByTenantIdAndBatchIdOrderByIdAsc(Long tenantId, String batchId);
}
