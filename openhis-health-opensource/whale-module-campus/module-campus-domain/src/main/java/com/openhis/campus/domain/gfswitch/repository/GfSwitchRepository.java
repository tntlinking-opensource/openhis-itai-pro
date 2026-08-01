/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.gfswitch.repository;

import com.openhis.campus.domain.gfswitch.entity.GfSwitch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GfSwitchRepository extends JpaRepository<GfSwitch, Long> {

    Optional<GfSwitch> findByTenantIdAndSwitchKeyAndIsDeletedFalse(Long tenantId, String switchKey);
}
