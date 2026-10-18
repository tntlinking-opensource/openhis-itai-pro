/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.archivetransfer.repository;

import com.zxplat.plugin.campus.domain.archivetransfer.entity.ArchiveTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArchiveTransferRepository extends JpaRepository<ArchiveTransfer, Long> {

    Page<ArchiveTransfer> findByTenantIdAndSubjectTypeEnumAndSubjectIdOrderByEffectiveDateDescOperateTimeDesc(
        Long tenantId, String subjectTypeEnum, Long subjectId, Pageable pageable);

    List<ArchiveTransfer> findByTenantIdAndSubjectTypeEnumAndSubjectIdOrderByEffectiveDateDescOperateTimeDesc(
        Long tenantId, String subjectTypeEnum, Long subjectId);
}
