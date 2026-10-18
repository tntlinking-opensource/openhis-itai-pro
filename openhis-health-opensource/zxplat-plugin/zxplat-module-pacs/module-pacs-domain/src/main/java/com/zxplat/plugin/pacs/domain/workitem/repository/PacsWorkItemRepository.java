/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.workitem.repository;

import com.zxplat.plugin.pacs.domain.workitem.entity.PacsWorkItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 检查工单聚合根仓储。
 */
public interface PacsWorkItemRepository extends JpaRepository<PacsWorkItem, Long>, JpaSpecificationExecutor<PacsWorkItem> {

    Optional<PacsWorkItem> findByAccessionNumberAndTenantId(String accessionNumber, Long tenantId);

    boolean existsByAccessionNumberAndTenantId(String accessionNumber, Long tenantId);
}
