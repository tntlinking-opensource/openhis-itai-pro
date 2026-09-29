/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.domain.imagingstudy.repository;

import com.openhis.whale.pacsimaging.domain.imagingstudy.entity.ImagingStudy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * ImagingStudy 聚合根仓储。
 */
public interface ImagingStudyRepository extends JpaRepository<ImagingStudy, Long> {

    Optional<ImagingStudy> findByWorkItemIdAndTenantId(Long workItemId, Long tenantId);

    Optional<ImagingStudy> findByStudyInstanceUidAndTenantId(String studyInstanceUid, Long tenantId);
}
