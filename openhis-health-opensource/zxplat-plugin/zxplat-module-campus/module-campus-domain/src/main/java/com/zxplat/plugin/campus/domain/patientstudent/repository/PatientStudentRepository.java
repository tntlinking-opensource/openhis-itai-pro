/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.patientstudent.repository;

import com.zxplat.plugin.campus.domain.patientstudent.entity.PatientStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface PatientStudentRepository
    extends JpaRepository<PatientStudent, Long>, JpaSpecificationExecutor<PatientStudent> {

    Optional<PatientStudent> findByTenantIdAndStudentNoAndIsDeletedFalse(Long tenantId, String studentNo);

    Optional<PatientStudent> findByTenantIdAndPatientIdAndIsDeletedFalse(Long tenantId, Long patientId);

    Optional<PatientStudent> findByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);

    boolean existsByTenantIdAndStudentNoAndIsDeletedFalse(Long tenantId, String studentNo);

    List<PatientStudent> findByTenantIdAndPatientIdIsNullAndIsDeletedFalseOrderByIdAsc(Long tenantId);
}
