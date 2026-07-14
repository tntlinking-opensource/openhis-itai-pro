/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.catalog.repository;

import com.openhis.elep.domain.catalog.model.ElepNationalCatalogRow;

import java.util.List;
import java.util.Optional;

/** 只读访问 {@code yb_national_catalog_item}（不依赖 module-chs-jilin-application）。 */
public interface ElepNationalCatalogReadRepository {

    long resolveCatalogEpochMillis(Long contractId, Long tenantId);

    long countRows(Long contractId, Long tenantId);

    List<ElepNationalCatalogRow> pageDistinctDrug(
            Long contractId,
            Long tenantId,
            String keyword,
            String hilistCodeExact,
            int limit,
            int offset);

    long countDistinctDrug(Long contractId, Long tenantId, String keyword, String hilistCodeExact);

    List<ElepNationalCatalogRow> page1301DetailDrug(
            Long contractId,
            Long tenantId,
            String keyword,
            String hilistCodeExact,
            String aprvno,
            String spec,
            int limit,
            int offset);

    long count1301DetailDrug(
            Long contractId,
            Long tenantId,
            String keyword,
            String hilistCodeExact,
            String aprvno,
            String spec);

    List<ElepNationalCatalogRow> pageDiagnosis(
            Long contractId,
            Long tenantId,
            String keyword,
            String hilistCodeExact,
            int limit,
            int offset);

    long countDiagnosis(Long contractId, Long tenantId, String keyword, String hilistCodeExact);

    Optional<ElepNationalCatalogRow> findDrugByCode(
            Long contractId,
            Long tenantId,
            String hilistCode,
            String aprvno,
            String spec);

    Optional<ElepNationalCatalogRow> findDiagnosisByCode(
            Long contractId,
            Long tenantId,
            String hilistCode);
}
