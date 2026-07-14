/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.domain.document.repository;

import com.openhis.whale.emr.domain.document.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 文书类型仓储。表 emr_document_type；code 为业务唯一键。
 */
@Repository
public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long>, JpaSpecificationExecutor<DocumentType> {

    /** 按编码查类型（含已软删，慎用）。 */
    Optional<DocumentType> findByCode(String code);

    /** 编码是否已占用。 */
    boolean existsByCode(String code);

    /** 按编码查未软删类型。 */
    Optional<DocumentType> findByCodeAndIsDeletedFalse(String code);

    /** 按主键查未软删类型。 */
    Optional<DocumentType> findByIdAndIsDeletedFalse(Long id);
}
