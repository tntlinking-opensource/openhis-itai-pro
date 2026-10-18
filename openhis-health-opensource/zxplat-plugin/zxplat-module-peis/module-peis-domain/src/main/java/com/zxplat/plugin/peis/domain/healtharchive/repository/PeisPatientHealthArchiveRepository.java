/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.healtharchive.repository;

import com.zxplat.plugin.peis.domain.healtharchive.entity.PeisPatientHealthArchive;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PeisPatientHealthArchiveRepository extends JpaRepository<PeisPatientHealthArchive, Long> {

    Optional<PeisPatientHealthArchive> findByTenantIdAndPatientIdAndIsDeletedFalse(Long tenantId, Long patientId);
}
