/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.patientstaff.repository;

import com.openhis.campus.domain.patientstaff.entity.PatientStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface PatientStaffRepository
    extends JpaRepository<PatientStaff, Long>, JpaSpecificationExecutor<PatientStaff> {

    Optional<PatientStaff> findByTenantIdAndEmployeeNoAndIsDeletedFalse(Long tenantId, String employeeNo);

    Optional<PatientStaff> findByTenantIdAndMedicalCodeAndIsDeletedFalse(Long tenantId, String medicalCode);

    Optional<PatientStaff> findByTenantIdAndPatientIdAndIsDeletedFalse(Long tenantId, Long patientId);

    Optional<PatientStaff> findByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);

    boolean existsByTenantIdAndEmployeeNoAndIsDeletedFalse(Long tenantId, String employeeNo);

    boolean existsByTenantIdAndMedicalCodeAndIsDeletedFalse(Long tenantId, String medicalCode);

    List<PatientStaff> findByTenantIdAndPatientIdIsNullAndIsDeletedFalseOrderByIdAsc(Long tenantId);
}
