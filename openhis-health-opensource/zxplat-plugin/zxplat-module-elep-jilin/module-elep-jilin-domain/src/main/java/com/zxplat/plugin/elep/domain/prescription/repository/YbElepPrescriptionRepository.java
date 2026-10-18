/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.repository;

import com.zxplat.plugin.elep.domain.prescription.entity.YbElepPrescription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface YbElepPrescriptionRepository extends JpaRepository<YbElepPrescription, Long> {

    Optional<YbElepPrescription> findByPrescriptionNoAndIsDeletedFalse(String prescriptionNo);

    Optional<YbElepPrescription> findByPrescriptionNoAndLocalStatusAndIsDeletedFalse(
            String prescriptionNo, String localStatus);

    List<YbElepPrescription> findByEncounterIdAndIsDeletedFalseOrderByUpdatedAtDesc(Long encounterId);

    Optional<YbElepPrescription> findByTenantIdAndContractBusNoAndHospRxnoAndIsDeletedFalse(
            Long tenantId, String contractBusNo, String hospRxno);

    Page<YbElepPrescription> findByPatientIdAndLocalStatusAndIsDeletedFalseOrderByPrscTimeDesc(
            Long patientId, String localStatus, Pageable pageable);
}
