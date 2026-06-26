/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.prescription.repository;

import com.openhis.elep.domain.prescription.entity.YbElepPrescriptionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface YbElepPrescriptionSnapshotRepository extends JpaRepository<YbElepPrescriptionSnapshot, Long> {
}
