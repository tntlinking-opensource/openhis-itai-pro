/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.repository;

import com.openhis.elep.domain.prescription.entity.YbElepUploadLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface YbElepUploadLogRepository extends JpaRepository<YbElepUploadLog, Long> {
}
