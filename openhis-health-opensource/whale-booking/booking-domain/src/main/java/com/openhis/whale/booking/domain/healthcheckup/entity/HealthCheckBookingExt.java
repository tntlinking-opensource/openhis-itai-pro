package com.openhis.whale.booking.domain.healthcheckup.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 体检预约扩展（bkg_health_check_booking_ext）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_health_check_booking_ext")
public class HealthCheckBookingExt extends BaseAuditedEntity {

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    @Column(name = "package_id")
    private Long packageId;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "package_snapshot_json")
    private String packageSnapshotJson;

    @Column(name = "estimated_amount")
    private BigDecimal estimatedAmount;
}
