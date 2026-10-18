/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.repository;

import com.zxplat.plugin.chs.domain.catalog.entity.YbNationalCatalogItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 国家目录镜像 JPA 仓储（仅派生方法Specification；复SQL {@link YbNationalCatalogItemLookup}） */
public interface YbNationalCatalogItemRepository extends JpaRepository<YbNationalCatalogItem, Long>,
    JpaSpecificationExecutor<YbNationalCatalogItem> {

    Optional<YbNationalCatalogItem> findByContractIdAndListTypeAndHilistCodeAndTenantIdAndIsDeletedFalse(
        Long contractId, String listType, String hilistCode, Long tenantId);

    /**
     * 按租户 + 多国家码精确匹配（支付对照解析，禁止 raw_json LIKE）。
     */
    List<YbNationalCatalogItem> findByTenantIdAndHilistCodeInAndIsDeletedFalse(
        Long tenantId, Collection<String> hilistCodes);

    Optional<YbNationalCatalogItem> findByContractIdAndListTypeAndSourceFileQuryNoAndSourceLineNoAndTenantIdAndIsDeletedFalse(
        Long contractId, String listType, String sourceFileQuryNo, Integer sourceLineNo, Long tenantId);

    boolean existsByContractIdAndListTypeAndSourceFileQuryNoAndTenantIdAndIsDeletedFalse(
        Long contractId, String listType, String sourceFileQuryNo, Long tenantId);

    long countByContractIdAndListTypeAndTenantIdAndIsDeletedFalse(
        Long contractId, String listType, Long tenantId);

    /** 租户下是否已有国家目录镜像（目录检查：无镜像时不报「镜像无此码」）。 */
    long countByTenantIdAndIsDeletedFalse(Long tenantId);
}
