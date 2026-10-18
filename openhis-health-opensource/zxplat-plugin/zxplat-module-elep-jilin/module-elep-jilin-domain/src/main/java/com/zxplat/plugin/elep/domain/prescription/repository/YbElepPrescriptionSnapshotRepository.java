/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.repository;

import com.zxplat.plugin.elep.domain.prescription.entity.YbElepPrescriptionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface YbElepPrescriptionSnapshotRepository extends JpaRepository<YbElepPrescriptionSnapshot, Long> {
}
