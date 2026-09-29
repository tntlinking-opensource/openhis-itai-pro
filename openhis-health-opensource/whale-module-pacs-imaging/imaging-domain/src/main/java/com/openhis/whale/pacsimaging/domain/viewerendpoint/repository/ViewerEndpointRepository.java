/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.domain.viewerendpoint.repository;

import com.openhis.whale.pacsimaging.domain.viewerendpoint.entity.ViewerEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * ViewerEndpoint 聚合根仓储。
 */
public interface ViewerEndpointRepository extends JpaRepository<ViewerEndpoint, Long> {

    List<ViewerEndpoint> findByTenantIdAndOrganizationIdOrderByCreatedAtDesc(Long tenantId, Long organizationId);

    List<ViewerEndpoint> findByTenantIdAndOrganizationIdIsNullOrderByCreatedAtDesc(Long tenantId);

    List<ViewerEndpoint> findByTenantIdAndEnabledTrue(Long tenantId);
}
