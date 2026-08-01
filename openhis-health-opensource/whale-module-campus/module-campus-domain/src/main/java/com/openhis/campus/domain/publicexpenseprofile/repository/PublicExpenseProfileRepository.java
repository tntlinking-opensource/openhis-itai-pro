/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.publicexpenseprofile.repository;

import com.openhis.campus.domain.publicexpenseprofile.entity.PublicExpenseProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface PublicExpenseProfileRepository
    extends JpaRepository<PublicExpenseProfile, Long>, JpaSpecificationExecutor<PublicExpenseProfile> {

    Optional<PublicExpenseProfile> findByTenantIdAndSubjectTypeEnumAndSubjectIdAndIsDeletedFalse(
        Long tenantId, String subjectTypeEnum, Long subjectId);

    Optional<PublicExpenseProfile> findByTenantIdAndCardNoAndIsDeletedFalse(Long tenantId, String cardNo);

    Optional<PublicExpenseProfile> findByTenantIdAndPatientIdAndIsDeletedFalse(Long tenantId, Long patientId);

    Optional<PublicExpenseProfile> findByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);
}
