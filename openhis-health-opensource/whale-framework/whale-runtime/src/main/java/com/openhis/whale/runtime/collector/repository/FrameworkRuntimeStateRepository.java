/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.runtime.collector.repository;

import com.openhis.whale.runtime.collector.domain.FrameworkRuntimeState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FrameworkRuntimeStateRepository extends JpaRepository<FrameworkRuntimeState, Long> {
}
