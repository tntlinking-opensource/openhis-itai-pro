/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.healthcheckup.repository;

import com.zxplat.plugin.booking.domain.healthcheckup.entity.HcSlotTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 体检号源模板仓储。
 */
public interface HcSlotTemplateRepository extends JpaRepository<HcSlotTemplate, Long>, JpaSpecificationExecutor<HcSlotTemplate> {

    Optional<HcSlotTemplate> findFirstByOrganizationIdAndIsDeletedFalse(Long organizationId);
}
