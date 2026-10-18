/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.healthcheckup.repository;

import com.zxplat.plugin.booking.domain.healthcheckup.entity.GroupCheckupBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface GroupCheckupBatchRepository extends JpaRepository<GroupCheckupBatch, Long>, JpaSpecificationExecutor<GroupCheckupBatch> {

    List<GroupCheckupBatch> findByOrgIdAndIsDeletedFalseOrderByBookingDateDesc(Long orgId);
}
