/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.document.repository;

import com.zxplat.plugin.emr.domain.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 文书仓储。表 emr_document；复杂列表走 {@link JpaSpecificationExecutor}。
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {

    /** 按主键查未软删文书。 */
    Optional<Document> findByIdAndIsDeletedFalse(Long id);

    /** 同就诊文书列表，按更新时间倒序；单次就诊文书量有限。 */
    List<Document> findByPatientIdAndEncounterIdAndIsDeletedFalseOrderByUpdatedAtDesc(
            String patientId, String encounterId);

    /** 同就诊同类型全部文书（历史列表）。 */
    List<Document> findByPatientIdAndEncounterIdAndDocumentTypeCodeAndIsDeletedFalse(
            String patientId, String encounterId, String documentTypeCode);

    /** 「打开文书」取同类型最新一条。 */
    Optional<Document> findFirstByPatientIdAndEncounterIdAndDocumentTypeCodeAndIsDeletedFalseOrderByCreatedAtDesc(
            String patientId, String encounterId, String documentTypeCode);

    /** 首次病程等同模板同就诊唯一性校验。 */
    boolean existsByEncounterIdAndSourceTemplateIdAndIsDeletedFalse(String encounterId, Long sourceTemplateId);
}
