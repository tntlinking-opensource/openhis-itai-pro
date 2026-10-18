/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.gfratiorequest.repository;

import com.zxplat.plugin.campus.domain.gfratiorequest.entity.GfRatioRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface GfRatioRequestRepository extends JpaRepository<GfRatioRequest, Long>, JpaSpecificationExecutor<GfRatioRequest> {

    Optional<GfRatioRequest> findByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);

    Optional<GfRatioRequest> findByTenantIdAndRequestNoAndIsDeletedFalse(Long tenantId, String requestNo);
}
