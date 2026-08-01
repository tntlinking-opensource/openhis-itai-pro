/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.servicerequestext.repository;

import com.openhis.health.domain.peis.servicerequestext.entity.PeisServiceRequestExt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * peis_service_request_ext 仓储（DM-PEIS-001）。
 */
public interface PeisServiceRequestExtRepository extends JpaRepository<PeisServiceRequestExt, Long> {

    List<PeisServiceRequestExt> findByServiceRequestIdInAndIsDeletedFalse(Collection<Long> serviceRequestIds);

    Optional<PeisServiceRequestExt> findByServiceRequestIdAndIsDeletedFalse(Long serviceRequestId);
}
