/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.chs.domain.catalog.repository;

import com.openhis.chs.domain.catalog.entity.YbNationalCatalogItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 国家目录镜像 JPA 仓储（仅派生方法与 Specification；复杂 SQL 见 {@link YbNationalCatalogItemLookup}）。
 */
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
