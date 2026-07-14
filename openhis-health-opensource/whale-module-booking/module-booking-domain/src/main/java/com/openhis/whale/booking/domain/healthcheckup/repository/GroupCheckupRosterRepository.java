/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.healthcheckup.repository;

import com.openhis.whale.booking.domain.healthcheckup.entity.GroupCheckupRoster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface GroupCheckupRosterRepository extends JpaRepository<GroupCheckupRoster, Long>, JpaSpecificationExecutor<GroupCheckupRoster> {

    List<GroupCheckupRoster> findByBatchIdAndIsDeletedFalse(Long batchId);

    List<GroupCheckupRoster> findByBatchIdAndBookingStatusCodeAndIsDeletedFalse(Long batchId, String bookingStatusCode);
}
