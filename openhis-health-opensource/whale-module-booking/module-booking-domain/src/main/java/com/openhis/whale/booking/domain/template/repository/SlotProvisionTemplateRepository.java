/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.template.repository;

import com.openhis.whale.booking.domain.template.entity.SlotProvisionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * T1 放号时段模板仓储。
 */
public interface SlotProvisionTemplateRepository extends JpaRepository<SlotProvisionTemplate, Long>,
    JpaSpecificationExecutor<SlotProvisionTemplate> {

    /**
     * 按科室与编码查有效模板。
     */
    Optional<SlotProvisionTemplate> findFirstByOrganizationIdAndTemplateCodeAndIsDeletedFalse(
        Long organizationId, String templateCode);
}
