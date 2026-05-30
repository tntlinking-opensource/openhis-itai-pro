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

import com.openhis.whale.emr.domain.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 文书仓储。表 emr_document。
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {

    Optional<Document> findByIdAndIsDeletedFalse(Long id);

    List<Document> findByPatientIdAndEncounterIdAndIsDeletedFalseOrderByUpdatedAtDesc(
            String patientId, String encounterId);

    List<Document> findByPatientIdAndEncounterIdAndDocumentTypeCodeAndIsDeletedFalse(
            String patientId, String encounterId, String documentTypeCode);

    Optional<Document> findFirstByPatientIdAndEncounterIdAndDocumentTypeCodeAndIsDeletedFalseOrderByCreatedAtDesc(
            String patientId, String encounterId, String documentTypeCode);

    boolean existsByEncounterIdAndSourceTemplateIdAndIsDeletedFalse(String encounterId, Long sourceTemplateId);
}
