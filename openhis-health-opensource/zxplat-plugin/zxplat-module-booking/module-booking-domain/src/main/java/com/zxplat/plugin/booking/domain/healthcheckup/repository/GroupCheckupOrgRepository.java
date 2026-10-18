/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.healthcheckup.repository;

import com.zxplat.plugin.booking.domain.healthcheckup.entity.GroupCheckupOrg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface GroupCheckupOrgRepository extends JpaRepository<GroupCheckupOrg, Long>, JpaSpecificationExecutor<GroupCheckupOrg> {

    Optional<GroupCheckupOrg> findByOrgCodeAndIsDeletedFalse(String orgCode);
}
