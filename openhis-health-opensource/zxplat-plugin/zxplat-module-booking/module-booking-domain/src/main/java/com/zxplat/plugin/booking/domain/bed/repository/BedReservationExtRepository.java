/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.bed.repository;

import com.zxplat.plugin.booking.domain.bed.entity.BedReservationExt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface BedReservationExtRepository extends JpaRepository<BedReservationExt, Long>, JpaSpecificationExecutor<BedReservationExt> {

    Optional<BedReservationExt> findByAppointmentIdAndIsDeletedFalse(Long appointmentId);

    List<BedReservationExt> findByLocationIdAndIsDeletedFalse(Long locationId);

    List<BedReservationExt> findByPatientIdAndIsDeletedFalse(Long patientId);
}
