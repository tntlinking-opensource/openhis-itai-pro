/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.repository;

import com.openhis.chs.domain.catalog.entity.YbNationalCatalogItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 国家目录镜像 JPA 仓储（仅派生方法Specification；复SQL {@link YbNationalCatalogItemLookup}） */
public interface YbNationalCatalogItemRepository extends JpaRepository<YbNationalCatalogItem, Long>,
    JpaSpecificationExecutor<YbNationalCatalogItem> {

    Optional<YbNationalCatalogItem> findByContractIdAndListTypeAndHilistCodeAndTenantIdAndIsDeletedFalse(
        Long contractId, String listType, String hilistCode, Long tenantId);

    Optional<YbNationalCatalogItem> findByContractIdAndListTypeAndSourceFileQuryNoAndSourceLineNoAndTenantIdAndIsDeletedFalse(
        Long contractId, String listType, String sourceFileQuryNo, Integer sourceLineNo, Long tenantId);

    boolean existsByContractIdAndListTypeAndSourceFileQuryNoAndTenantIdAndIsDeletedFalse(
        Long contractId, String listType, String sourceFileQuryNo, Long tenantId);

    long countByContractIdAndListTypeAndTenantIdAndIsDeletedFalse(
        Long contractId, String listType, Long tenantId);
}
