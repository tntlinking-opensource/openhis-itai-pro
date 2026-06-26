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

import com.openhis.chs.domain.catalog.entity.YbCatalogDownloadCursor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

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
