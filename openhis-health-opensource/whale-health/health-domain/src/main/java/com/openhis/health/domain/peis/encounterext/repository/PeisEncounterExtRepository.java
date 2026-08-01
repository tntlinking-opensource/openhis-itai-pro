/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.encounterext.repository;

import com.openhis.health.domain.peis.encounterext.entity.PeisEncounterExt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * peis_encounter_ext 仓储（DM-PEIS-007）。
 */
public interface PeisEncounterExtRepository extends JpaRepository<PeisEncounterExt, Long> {

    Optional<PeisEncounterExt> findByEncounterIdAndIsDeletedFalse(Long encounterId);

    List<PeisEncounterExt> findByEncounterIdInAndIsDeletedFalse(Collection<Long> encounterIds);

    Optional<PeisEncounterExt> findByBusNoAndIsDeletedFalse(String busNo);

    Optional<PeisEncounterExt> findByBookingAppointmentIdAndIsDeletedFalse(Long appointmentId);

    boolean existsByEncounterIdAndIsDeletedFalse(Long encounterId);
}
