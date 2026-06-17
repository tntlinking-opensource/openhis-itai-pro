package com.openhis.whale.booking.domain.bed.repository;

import com.openhis.whale.booking.domain.bed.entity.BedReservationExt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface BedReservationExtRepository extends JpaRepository<BedReservationExt, Long>, JpaSpecificationExecutor<BedReservationExt> {

    Optional<BedReservationExt> findByAppointmentIdAndIsDeletedFalse(Long appointmentId);

    List<BedReservationExt> findByPatientIdAndIsDeletedFalse(Long patientId);
}
