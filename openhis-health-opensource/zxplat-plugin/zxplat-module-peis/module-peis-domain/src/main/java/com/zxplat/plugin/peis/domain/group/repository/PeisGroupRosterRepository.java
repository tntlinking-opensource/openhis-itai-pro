/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.group.repository;

import com.zxplat.plugin.peis.domain.group.entity.PeisGroupRoster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface PeisGroupRosterRepository
    extends JpaRepository<PeisGroupRoster, Long>, JpaSpecificationExecutor<PeisGroupRoster> {

    List<PeisGroupRoster> findByBatchIdAndIsDeletedFalse(Long batchId);

    List<PeisGroupRoster> findByBatchIdAndPayStatusCodeAndIsDeletedFalse(Long batchId, String payStatusCode);

    List<PeisGroupRoster> findByPatientIdAndIsDeletedFalseOrderByUpdatedAtDesc(Long patientId);

    Optional<PeisGroupRoster> findByIdAndIsDeletedFalse(Long id);

    Optional<PeisGroupRoster> findByEncounterIdAndIsDeletedFalse(Long encounterId);

    boolean existsByBatchIdAndPatientIdAndIsDeletedFalse(Long batchId, Long patientId);

    boolean existsByPatientIdAndIsDeletedFalse(Long patientId);

    boolean existsByPatientIdAndIsDeletedFalseAndIdNot(Long patientId, Long excludeRosterId);

    boolean existsByIdCardNoAndIsDeletedFalse(String idCardNo);

    boolean existsByIdCardNoAndIsDeletedFalseAndIdNot(String idCardNo, Long excludeRosterId);

    boolean existsByStudentNoAndIsDeletedFalse(String studentNo);

    boolean existsByStudentNoAndIsDeletedFalseAndIdNot(String studentNo, Long excludeRosterId);

    boolean existsByEmployeeNoAndIsDeletedFalse(String employeeNo);

    boolean existsByEmployeeNoAndIsDeletedFalseAndIdNot(String employeeNo, Long excludeRosterId);

    List<PeisGroupRoster> findByBatchIdInAndIsDeletedFalse(List<Long> batchIds);
}
