/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.policy.repository;

import com.openhis.whale.booking.domain.policy.entity.ReleasePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ReleasePolicyRepository extends JpaRepository<ReleasePolicy, Long>, JpaSpecificationExecutor<ReleasePolicy> {

    Optional<ReleasePolicy> findByOrganizationIdAndPolicyCodeAndIsDeletedFalse(Long organizationId, String policyCode);
}
