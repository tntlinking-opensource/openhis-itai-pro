package com.openhis.whale.booking.domain.healthcheckup.repository;

import com.openhis.whale.booking.domain.healthcheckup.entity.HealthCheckBookingExt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface HealthCheckBookingExtRepository extends JpaRepository<HealthCheckBookingExt, Long>, JpaSpecificationExecutor<HealthCheckBookingExt> {

    Optional<HealthCheckBookingExt> findByAppointmentIdAndIsDeletedFalse(Long appointmentId);
}
