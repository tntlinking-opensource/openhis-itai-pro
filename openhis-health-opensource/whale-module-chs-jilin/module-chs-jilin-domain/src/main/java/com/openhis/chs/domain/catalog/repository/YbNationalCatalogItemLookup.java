/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.repository;

import com.openhis.chs.domain.catalog.entity.YbNationalCatalogItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 国家目录镜像复杂只读查询（PostgreSQL DISTINCT ON / 1301 明细）；Repository 接口号{@code @Query} */
public interface YbNationalCatalogItemLookup {

    Page<YbNationalCatalogItem> pageDistinctHilistCode(
        Long contractId,
        Long tenantId,
        String listType,
        boolean picker1312Only,
        String sourceInfno,
        String valiFlag,
        String keyword,
        String hilistCodePrefix,
        String aprvno,
        String spec,
        Pageable pageable);

    List<YbNationalCatalogItem> listDistinctForMedicationMatch(
        Long contractId,
        Long tenantId,
        String listType,
        String keyword,
        String aprvno,
        String spec,
        String hilistCodeExact,
        int limit);

    List<YbNationalCatalogItem> list1301DetailForMedicationMatch(
        Long contractId,
        Long tenantId,
        String listType,
        String keyword,
        String aprvno,
        String spec,
        String hilistCodeExact,
        String valiFlag,
        int limit);

    Page<YbNationalCatalogItem> page1301Detail(
        Long contractId,
        Long tenantId,
        String listType,
        String valiFlag,
        String keyword,
        String hilistCodePrefix,
        String aprvno,
        String spec,
        Pageable pageable);
}
