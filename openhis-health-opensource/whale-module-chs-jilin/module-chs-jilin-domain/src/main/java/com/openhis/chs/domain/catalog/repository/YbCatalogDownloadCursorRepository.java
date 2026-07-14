/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.repository;

import com.openhis.chs.domain.catalog.entity.YbCatalogDownloadCursor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** {@code yb_catalog_download_cursor} JPA 仓储 */
public interface YbCatalogDownloadCursorRepository extends JpaRepository<YbCatalogDownloadCursor, Long> {

    List<YbCatalogDownloadCursor> findByContractIdAndTenantIdAndIsDeletedFalseOrderByInfnoAsc(
        Long contractId, Long tenantId);

    Optional<YbCatalogDownloadCursor> findByContractIdAndInfnoAndTenantIdAndIsDeletedFalse(
        Long contractId, String infno, Long tenantId);

    Optional<YbCatalogDownloadCursor> findFirstByContractIdAndInfnoAndTenantIdAndIsDeletedFalseOrderByLastSuccessAtDescIdDesc(
        Long contractId, String infno, Long tenantId);

    List<YbCatalogDownloadCursor> findByContractIdAndTenantIdAndIsDeletedFalseOrderByLastSuccessAtDescIdDesc(
        Long contractId, Long tenantId);

    List<YbCatalogDownloadCursor> findByContractIdAndTenantIdAndIsDeletedFalseOrderByLastSuccessAtDescIdDesc(
        Long contractId, Long tenantId, Pageable pageable);
}
