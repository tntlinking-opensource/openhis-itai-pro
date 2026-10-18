/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.queue.repository;

import com.zxplat.plugin.booking.domain.queue.entity.QueueDisplayConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface QueueDisplayConfigRepository extends JpaRepository<QueueDisplayConfig, Long>, JpaSpecificationExecutor<QueueDisplayConfig> {

    Optional<QueueDisplayConfig> findByOrganizationIdAndLocationIdAndIsDeletedFalse(Long organizationId, Long locationId);

    List<QueueDisplayConfig> findByOrganizationIdAndIsDeletedFalse(Long organizationId);
}
