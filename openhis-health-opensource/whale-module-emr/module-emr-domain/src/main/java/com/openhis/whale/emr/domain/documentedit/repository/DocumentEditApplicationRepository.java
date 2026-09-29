/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.domain.documentedit.repository;

import com.openhis.whale.emr.domain.documentedit.entity.DocumentEditApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 文书编辑申请仓储。
 */
public interface DocumentEditApplicationRepository
        extends JpaRepository<DocumentEditApplication, Long>, JpaSpecificationExecutor<DocumentEditApplication> {

    Optional<DocumentEditApplication> findByIdAndIsDeletedFalse(Long id);

    List<DocumentEditApplication> findByTenantIdAndDocumentIdAndStatusEnumInAndIsDeletedFalse(
            Long tenantId, Long documentId, List<String> statusEnums);

    List<DocumentEditApplication> findByDocumentIdAndIsDeletedFalseOrderByAppliedAtDesc(Long documentId);
}
