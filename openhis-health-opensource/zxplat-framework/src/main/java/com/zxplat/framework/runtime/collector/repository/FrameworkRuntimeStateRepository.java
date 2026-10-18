/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.runtime.collector.repository;

import com.zxplat.framework.runtime.collector.domain.FrameworkRuntimeState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FrameworkRuntimeStateRepository extends JpaRepository<FrameworkRuntimeState, Long> {
}
